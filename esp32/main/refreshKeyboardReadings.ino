// ============================================================
// refreshKeyboardReadings.ino
//
// DISPLAY_BTN (pin 26) — falling-edge debounce, advances
//   oledState through 5 states: 0 → 1 → 2 → 3 → 4 → 0
//
// WIFI_BTN (pin 25) — falling-edge debounce, triggers AP
//   configuration mode (sets AP_mode_status = true).
//
// Debounce window: 50 ms (both buttons).
// ============================================================

#define NUM_OLED_STATES 5   // 0: Sound  1: Temp/Hum
                            // 2: Leq chart  3: T/H chart  4: RSSI chart

static bool          lastDisplayBtnRaw    = HIGH;
static bool          displayBtnDebounced  = HIGH;
static unsigned long displayDebounceStart = 0;

static bool          lastWifiBtnRaw       = HIGH;
static bool          wifiBtnDebounced     = HIGH;
static unsigned long wifiDebounceStart    = 0;

const unsigned long DEBOUNCE_MS = 50;

void refreshKeyboardReadings() {
  unsigned long now = millis();

  // ── DISPLAY button ────────────────────────────────────────
  bool rawDisplay = digitalRead(DISPLAY_BTN);
  if (rawDisplay != lastDisplayBtnRaw) {
    displayDebounceStart = now;
    lastDisplayBtnRaw    = rawDisplay;
  }
  if ((now - displayDebounceStart) >= DEBOUNCE_MS) {
    bool prev           = displayBtnDebounced;
    displayBtnDebounced = rawDisplay;
    if (prev == HIGH && displayBtnDebounced == LOW) {
      oledState = (oledState + 1) % NUM_OLED_STATES;
      Serial.printf("[BTN] OLED state → %d/%d\n",
                    oledState + 1, NUM_OLED_STATES);
    }
  }

  // ── WIFI button ───────────────────────────────────────────
  bool rawWifi = digitalRead(WIFI_BTN);
  if (rawWifi != lastWifiBtnRaw) {
    wifiDebounceStart = now;
    lastWifiBtnRaw    = rawWifi;
  }
  if ((now - wifiDebounceStart) >= DEBOUNCE_MS) {
    bool prev       = wifiBtnDebounced;
    wifiBtnDebounced = rawWifi;
    if (prev == HIGH && wifiBtnDebounced == LOW) {
      AP_mode_status = true;
      statusLED      = true;
      digitalWrite(LED, HIGH);
      Serial.println("[BTN] WiFi AP mode triggered.");
    }
  }
}
