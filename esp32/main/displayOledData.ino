// ============================================================
// displayOledData.ino — 5-state OLED state machine (SSD1306 128×64)
//
// State 0 — Numeric:  Sound   (Leq / Lmax / Lmin)           [1/5]
// State 1 — Numeric:  Temp & Humidity                        [2/5]
// State 2 — Chart:    Leq over time (sparkline)              [3/5]
// State 3 — Chart:    Temp (solid) & Humidity (dashed)       [4/5]
//                     dual Y-axis: left=°C, right=%
// State 4 — Chart:    Wi-Fi RSSI over time (dBm)             [5/5]
//
// Wi-Fi icon: 4-bar signal strength drawn in the footer of
//             all numeric screens and next to the time label
//             on all chart screens.
//
// Shared chart geometry (states 2-4):
//   Header bar  : y=0..9   (10 px, white-filled, black text)
//   Chart area  : y=11..52 (42 px tall)
//   Footer strip: y=54..63 (time label + RSSI icon)
//
// Left-margin for Y labels is 24 px on states 2 & 4 (single axis).
// States 0 & 1 use 20 px left / 20 px right for dual-axis labels.
// ============================================================

// ── Forward declarations ──────────────────────────────────────
void drawState0_SoundNumeric();
void drawState1_TempHumNumeric();
void drawState2_LeqChart();
void drawState3_TempHumChart();
void drawState4_RSSIChart();
void drawWifiRSSI(uint8_t x, uint8_t y);

// ── Pixel-mapping helpers ─────────────────────────────────────

// valueToY: map a data value to a pixel row (0=top of display)
static inline int16_t valueToY(float v, float vMin, float vMax,
                                uint8_t y0, uint8_t h) {
  if (vMax <= vMin) return (int16_t)(y0 + h / 2);
  float norm = (v - vMin) / (vMax - vMin);
  return (int16_t)(y0 + h - 1 - norm * (h - 1));
}

// historyCount: valid entries in the ring buffer
static uint8_t historyCount() {
  return historyFilled ? CHART_POINTS : historyIndex;
}

// ringGet: chronological access (0 = oldest, n-1 = newest)
static float ringGet(const float* arr, uint8_t n, uint8_t i) {
  return historyFilled ? arr[(historyIndex + i) % CHART_POINTS]
                       : arr[i];
}

// autoRange: compute a "nice" axis range for a set of ring-buffer values.
//   snapStep — round min down / max up to nearest multiple
//   minSpan  — guaranteed minimum axis span
static void autoRange(const float* arr, uint8_t n,
                      float snapStep, float minSpan,
                      float& outMin, float& outMax) {
  float rawMin =  1e9f, rawMax = -1e9f;
  for (uint8_t i = 0; i < n; i++) {
    float v = ringGet(arr, n, i);
    if (v < rawMin) rawMin = v;
    if (v > rawMax) rawMax = v;
  }
  outMin = floorf(rawMin / snapStep) * snapStep - snapStep;
  outMax = ceilf (rawMax / snapStep) * snapStep + snapStep;
  if (outMax - outMin < minSpan) {
    float mid = (outMin + outMax) * 0.5f;
    outMin = mid - minSpan * 0.5f;
    outMax = mid + minSpan * 0.5f;
  }
}

// drawSparkline: render a connected line through ring-buffer data
static void drawSparkline(const float* arr, uint8_t n,
                          uint8_t cx, uint8_t cy, uint8_t cw, uint8_t ch,
                          float yMin, float yMax,
                          bool dashed = false) {
  float xStep = (float)(cw - 1) / (float)(n - 1);
  for (uint8_t i = 1; i < n; i++) {
    if (dashed && (i % 2 == 0)) continue;
    float   v0 = ringGet(arr, n, i - 1);
    float   v1 = ringGet(arr, n, i);
    int16_t x0 = cx + (int16_t)((i - 1) * xStep);
    int16_t x1 = cx + (int16_t)(i       * xStep);
    int16_t y0 = valueToY(v0, yMin, yMax, cy, ch);
    int16_t y1 = valueToY(v1, yMin, yMax, cy, ch);
    display.drawLine(x0, y0, x1, y1, WHITE);
  }
}

// drawNewestDot + label at the rightmost data point
static void drawNewestDot(const float* arr, uint8_t n,
                          uint8_t cx, uint8_t cy, uint8_t cw, uint8_t ch,
                          float yMin, float yMax,
                          bool hollow = false) {
  float   v  = ringGet(arr, n, n - 1);
  float   xStep = (float)(cw - 1) / (float)(n - 1);
  int16_t px = cx + (int16_t)((n - 1) * xStep);
  int16_t py = valueToY(v, yMin, yMax, cy, ch);
  if (hollow) display.drawCircle(px, py, 2, WHITE);
  else        display.fillCircle(px, py, 2, WHITE);
}

// drawMidGrid: dotted horizontal mid-line across chart area
static void drawMidGrid(uint8_t cx, uint8_t cy, uint8_t cw, uint8_t ch) {
  uint8_t midY = cy + ch / 2;
  for (uint8_t x = cx; x < cx + cw; x += 4) {
    display.drawPixel(x, midY, WHITE);
  }
}

// drawAxes: left Y-axis + bottom X-axis
static void drawAxes(uint8_t cx, uint8_t cy, uint8_t cw, uint8_t ch) {
  display.drawLine(cx - 1, cy,        cx - 1,      cy + ch - 1, WHITE);
  display.drawLine(cx - 1, cy + ch-1, cx + cw - 1, cy + ch - 1, WHITE);
}

// drawHeader: inverted white bar with black text, state indicator
static void drawHeader(const char* label, uint8_t state, uint8_t total) {
  display.fillRect(0, 0, SCREEN_WIDTH, 10, WHITE);
  display.setTextColor(BLACK);
  display.setTextSize(1);
  display.setCursor(2, 1);
  display.print(label);
  // State indicator right-aligned
  char buf[8]; snprintf(buf, sizeof(buf), "[%d/%d]", state, total);
  display.setCursor(SCREEN_WIDTH - strlen(buf) * 6 - 1, 1);
  display.print(buf);
  display.setTextColor(WHITE);
}

// drawFooter: timestamp + RSSI icon on bottom strip
static void drawFooter() {
  display.setTextSize(1);
  display.drawLine(0, 55, SCREEN_WIDTH - 1, 55, WHITE);
  display.setCursor(0, 57);
  display.print(convertDateTimeToOledDisplay(reading_time));
  drawWifiRSSI(114, 56);
}

// drawChartFooter: time-span label + RSSI icon (for chart screens)
static void drawChartFooter(uint8_t cx, uint8_t n) {
  display.setTextSize(1);
  display.setCursor(cx, 57);
  display.print("last ");
  display.print(n * (PUBLISH_INTERVAL / 1000));
  display.print("s");
  drawWifiRSSI(114, 56);
}

// ─────────────────────────────────────────────────────────────
// drawWifiRSSI
//
// 4-bar Wi-Fi signal strength icon drawn at pixel (x, y).
// Icon: 13 px wide × 9 px tall. Bottom-aligned bars.
// Threshold mapping:
//   wifiRSSI == 1          → 0 bars (sentinel: not connected)
//   ≤ -80 dBm (very weak)  → 1 bar
//   ≤ -67 dBm (weak)       → 2 bars
//   ≤ -55 dBm (fair)       → 3 bars
//   > -55 dBm (good/great) → 4 bars
// ─────────────────────────────────────────────────────────────
void drawWifiRSSI(uint8_t x, uint8_t y) {
  const uint8_t barW   = 2;
  const uint8_t gap    = 1;
  const uint8_t barH[] = {3, 5, 7, 9};

  uint8_t bars = 0;
  if (wifiRSSI != 1) {
    if      (wifiRSSI > -55) bars = 4;
    else if (wifiRSSI > -67) bars = 3;
    else if (wifiRSSI > -80) bars = 2;
    else                     bars = 1;
  }

  for (uint8_t i = 0; i < 4; i++) {
    uint8_t bx = x + i * (barW + gap);
    uint8_t by = y + (9 - barH[i]);
    if (i < bars) display.fillRect(bx, by, barW, barH[i], WHITE);
    else          display.drawRect(bx, by, barW, barH[i], WHITE);
  }
}

// =============================================================
// displayOledData — entry point called from loop()
// =============================================================
void displayOledData() {
  // AP mode screen takes highest priority
  if (AP_mode_status) {
    display.clearDisplay();
    display.setTextSize(1);
    display.setCursor(0,  0); display.print("WiFi Setup");
    display.setCursor(0, 16); display.print("Connect to AP:");
    display.setCursor(0, 30); display.print("ESP32-Config");
    display.setCursor(0, 46); display.print("192.168.4.1");
    display.display();
    configure_WiFi();
    if (WiFi.status() == WL_CONNECTED) AP_mode_status = false;
    return;
  }

  switch (oledState) {
    case 0: drawState0_SoundNumeric();   break;
    case 1: drawState1_TempHumNumeric(); break;
    case 2: drawState2_LeqChart();       break;
    case 3: drawState3_TempHumChart();   break;
    case 4: drawState4_RSSIChart();      break;
    default: oledState = 0;              break;
  }
}

// =============================================================
// STATE 0 — Numeric Sound Display
// =============================================================
void drawState0_SoundNumeric() {
  display.clearDisplay();
  drawHeader("SOUND LEVEL", 1, 5);

  // Leq — large, primary metric
  display.setTextSize(1);
  display.setCursor(0, 13);
  display.print("Leq");

  display.setTextSize(2);
  display.setCursor(30, 11);
  display.print(leqSPL, 1);
  display.print("dB");

  // Lmax
  display.setTextSize(1);
  display.setCursor(0, 33);
  display.print("Lmax:");
  display.setCursor(36, 33);
  display.print(lmaxSPL, 1);
  display.print(" dB");

  // Lmin
  display.setCursor(0, 44);
  display.print("Lmin:");
  display.setCursor(36, 44);
  display.print(lminSPL, 1);
  display.print(" dB");

  drawFooter();
  display.display();
}

// =============================================================
// STATE 1 — Numeric Temperature & Humidity Display
// =============================================================
void drawState1_TempHumNumeric() {
  display.clearDisplay();
  drawHeader("TEMP & HUMIDITY", 2, 5);

  // Temperature
  display.setTextSize(1);
  display.setCursor(0, 13);
  display.print("Temp");

  display.setTextSize(2);
  display.setCursor(30, 11);
  display.print(temperature, 1);
  display.print(" C");
  // Degree symbol (small circle above "C")
  display.drawCircle(30 + 6 * 4, 12, 2, WHITE);

  // Divider
  display.drawLine(0, 33, SCREEN_WIDTH - 1, 33, WHITE);

  // Humidity
  display.setTextSize(1);
  display.setCursor(0, 36);
  display.print("Hum");

  display.setTextSize(2);
  display.setCursor(30, 35);
  display.print(humidity, 1);
  display.print(" %");

  drawFooter();
  display.display();
}

// =============================================================
// STATE 2 — Leq Sparkline Chart
// =============================================================
void drawState2_LeqChart() {
  display.clearDisplay();
  drawHeader("Leq OVER TIME", 3, 5);

  uint8_t n = historyCount();
  if (n < 2) {
    display.setCursor(10, 30); display.print("Collecting data...");
    display.display(); return;
  }

  // Chart area: 24 px left margin for Y labels
  const uint8_t CX=24, CY=11, CW=103, CH=42;

  float dMin, dMax;
  autoRange(splHistory, n, 5.0f, 10.0f, dMin, dMax);

  // Y-axis labels (top / mid / bottom)
  display.setTextSize(1);
  display.setCursor(0, CY);           display.print((int)dMax); display.print("d");
  display.setCursor(0, CY+CH/2-4);   display.print((int)((dMin+dMax)/2.0f));
  display.setCursor(0, CY+CH-8);     display.print((int)dMin);

  drawAxes(CX, CY, CW, CH);
  drawMidGrid(CX, CY, CW, CH);
  drawSparkline(splHistory, n, CX, CY, CW, CH, dMin, dMax);
  drawNewestDot(splHistory, n, CX, CY, CW, CH, dMin, dMax);

  // Latest value top-right
  float newest = ringGet(splHistory, n, n-1);
  display.setCursor(88, CY); display.print(newest, 1);

  drawChartFooter(CX, n);
  display.display();
}

// =============================================================
// STATE 3 — Dual-axis Temp & Humidity Chart
// Temperature: left Y-axis (solid line)
// Humidity:    right Y-axis (dashed line)
// =============================================================
void drawState3_TempHumChart() {
  display.clearDisplay();
  drawHeader("T(\xF8" "C) & RH(%)", 4, 5);

  uint8_t n = historyCount();
  if (n < 2) {
    display.setCursor(10, 30); display.print("Collecting data...");
    display.display(); return;
  }

  // Chart area: 20 px left (temp labels), 20 px right (hum labels)
  const uint8_t CX=20, CY=11, CW=88, CH=42;
  const uint8_t CXR = CX + CW;

  float tMin, tMax, hMin, hMax;
  autoRange(tempHistory, n, 2.0f, 4.0f, tMin, tMax);
  autoRange(humHistory,  n, 5.0f, 10.0f, hMin, hMax);
  hMin = constrain(hMin, 0.0f, 100.0f);
  hMax = constrain(hMax, 0.0f, 100.0f);

  display.setTextSize(1);

  // Left Y labels (Temperature)
  display.setCursor(0, CY);          display.print((int)tMax);
  display.setCursor(0, CY+CH/2-4);  display.print((int)((tMin+tMax)/2.0f));
  display.setCursor(0, CY+CH-8);    display.print((int)tMin);

  // Right Y labels (Humidity)
  display.setCursor(CXR+2, CY);         display.print((int)hMax);
  display.setCursor(CXR+2, CY+CH/2-4); display.print((int)((hMin+hMax)/2.0f));
  display.setCursor(CXR+2, CY+CH-8);   display.print((int)hMin);

  // Axes (left Y, right Y, bottom X)
  display.drawLine(CX-1, CY,      CX-1, CY+CH-1, WHITE);
  display.drawLine(CXR,  CY,      CXR,  CY+CH-1, WHITE);
  display.drawLine(CX-1, CY+CH-1, CXR,  CY+CH-1, WHITE);
  drawMidGrid(CX, CY, CW, CH);

  // Temperature — solid line + filled dot
  drawSparkline(tempHistory, n, CX, CY, CW, CH, tMin, tMax, false);
  drawNewestDot(tempHistory, n, CX, CY, CW, CH, tMin, tMax, false);

  // Humidity — dashed line + hollow dot
  drawSparkline(humHistory,  n, CX, CY, CW, CH, hMin, hMax, true);
  drawNewestDot(humHistory,  n, CX, CY, CW, CH, hMin, hMax, true);

  // Legend (inside chart, top-left corner)
  display.fillCircle(CX+4,  CY+4, 2, WHITE);
  display.setCursor (CX+9,  CY);   display.print("T");
  display.drawCircle(CX+22, CY+4, 2, WHITE);
  display.setCursor (CX+27, CY);   display.print("H");

  drawChartFooter(CX, n);
  display.display();
}

// =============================================================
// STATE 4 — Wi-Fi RSSI Chart over time
//
// Y-axis: dBm, displayed as negative integers.
//   Top of chart = strongest signal (e.g. -20 dBm)
//   Bottom       = weakest  (e.g. -100 dBm)
//
// Coloured zones drawn as dotted horizontal markers:
//   > -55 dBm  : excellent  (top zone)
//   > -67 dBm  : good
//   > -80 dBm  : fair
//   ≤ -80 dBm  : poor       (bottom zone)
//
// A disconnected sentinel (value 1) is omitted from the range
// calculation and displayed as a gap in the line.
// =============================================================
void drawState4_RSSIChart() {
  display.clearDisplay();
  drawHeader("WiFi RSSI (dBm)", 5, 5);

  uint8_t n = historyCount();
  if (n < 2) {
    display.setCursor(10, 30); display.print("Collecting data...");
    display.display(); return;
  }

  // Chart area: 24 px left margin (labels like "-80")
  const uint8_t CX=24, CY=11, CW=103, CH=42;

  // ── Y-axis range — skip sentinel values (1) ───────────────
  float rMin = -100.0f, rMax = -20.0f;  // reasonable defaults
  bool  hasData = false;
  for (uint8_t i = 0; i < n; i++) {
    float v = ringGet(rssiHistory, n, i);
    if (v >= 0.0f) continue;             // skip sentinel (1) & 0
    if (!hasData) { rMin = v; rMax = v; hasData = true; }
    else {
      if (v < rMin) rMin = v;
      if (v > rMax) rMax = v;
    }
  }
  // Snap to 10 dBm grid, min 20 dBm span
  rMin = floorf(rMin / 10.0f) * 10.0f - 10.0f;
  rMax = ceilf (rMax / 10.0f) * 10.0f + 10.0f;
  if (rMax - rMin < 20.0f) { float mid=(rMin+rMax)*0.5f; rMin=mid-10.0f; rMax=mid+10.0f; }

  display.setTextSize(1);

  // Y-axis labels (negative dBm values, 3 chars wide)
  display.setCursor(0, CY);         display.print((int)rMax);
  display.setCursor(0, CY+CH/2-4); display.print((int)((rMin+rMax)/2.0f));
  display.setCursor(0, CY+CH-8);   display.print((int)rMin);

  drawAxes(CX, CY, CW, CH);

  // ── Quality zone dotted markers ───────────────────────────
  // Each threshold drawn as a dotted horizontal line if it falls
  // within the current axis range.
  const float zones[] = {-55.0f, -67.0f, -80.0f};
  for (uint8_t z = 0; z < 3; z++) {
    float threshold = zones[z];
    if (threshold > rMin && threshold < rMax) {
      int16_t zy = valueToY(threshold, rMin, rMax, CY, CH);
      for (uint8_t x = CX; x < CX + CW; x += 6) {
        display.drawPixel(x,   zy, WHITE);
        display.drawPixel(x+1, zy, WHITE);
      }
    }
  }

  // ── RSSI sparkline (skip sentinel segments) ───────────────
  float xStep = (float)(CW - 1) / (float)(n - 1);
  for (uint8_t i = 1; i < n; i++) {
    float v0 = ringGet(rssiHistory, n, i - 1);
    float v1 = ringGet(rssiHistory, n, i);
    // Skip segments that include a sentinel value
    if (v0 >= 0.0f || v1 >= 0.0f) continue;
    int16_t x0 = CX + (int16_t)((i-1) * xStep);
    int16_t x1 = CX + (int16_t)(i     * xStep);
    int16_t y0 = valueToY(v0, rMin, rMax, CY, CH);
    int16_t y1 = valueToY(v1, rMin, rMax, CY, CH);
    display.drawLine(x0, y0, x1, y1, WHITE);
  }

  // ── Newest valid value — dot + label ──────────────────────
  // Walk back from newest to find the first non-sentinel
  for (int8_t i = n - 1; i >= 0; i--) {
    float v = ringGet(rssiHistory, n, (uint8_t)i);
    if (v >= 0.0f) continue;
    float   xS = (float)(CW - 1) / (float)(n - 1);
    int16_t px = CX + (int16_t)(i * xS);
    int16_t py = valueToY(v, rMin, rMax, CY, CH);
    display.fillCircle(px, py, 2, WHITE);
    // Numeric label: top-right area
    display.setCursor(85, CY);
    display.print((int)v);
    display.print("d");
    break;
  }

  // ── Signal quality text label ──────────────────────────────
  const char* quality = "NC";
  if (wifiRSSI != 1) {
    if      (wifiRSSI > -55) quality = "Excellent";
    else if (wifiRSSI > -67) quality = "Good";
    else if (wifiRSSI > -80) quality = "Fair";
    else                     quality = "Poor";
  }
  display.setCursor(CX, CY);
  display.print(quality);

  drawChartFooter(CX, n);
  display.display();
}
