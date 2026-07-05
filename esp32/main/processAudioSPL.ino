// ============================================================
// processAudioSPL.ino
//
// TWO roles in one file:
//
// 1. REAL SENSOR — processAudioSPL()
//    Samples the MCP602 microphone via ADC on AUDIO_PIN and
//    accumulates IEC 61672 Leq, Lmax and Lmin every 50 ms.
//    Globals written: leqSPL, lmaxSPL, lminSPL (declared in main.ino).
//    Call resetSPLAccumulators() after each publish cycle.
//
// 2. SIMULATION ENGINE — generateSimulatedReadings()
//    Produces per-device unbalanced sensor readings that mimic
//    realistic restaurant conditions.  Called once per publish
//    cycle inside sendDataViaMQTT for each IoTDevice.
//    Uses a Box-Muller Gaussian noise generator so values drift
//    naturally around the device's SimProfile bias.
//
// ============================================================

// ── ADC calibration ──────────────────────────────────────────
#define SPL_MIN_DB   30.0f   // dB SPL @ ADC p-p = 0    (very quiet)
#define SPL_MAX_DB  120.0f   // dB SPL @ ADC p-p = 4095 (extreme)

// ── Leq accumulator (static = survives between loop() calls) ─
static float    linearEnergySum = 0.0f;
static uint32_t sampleCount     = 0;
static bool     firstSample     = true;

// ─────────────────────────────────────────────────────────────
// processAudioSPL — call every loop() iteration.
// Samples ADC for 50 ms; accumulates Leq energy, tracks peak/floor.
// ─────────────────────────────────────────────────────────────
void processAudioSPL() {
  const unsigned long SAMPLE_WINDOW_MS = 50;
  unsigned long startMs = millis();

  unsigned int signalMax = 0;
  unsigned int signalMin = 4095;

  while (millis() - startMs < SAMPLE_WINDOW_MS) {
    unsigned int s = analogRead(AUDIO_PIN);
    if (s < 4095) {
      if (s > signalMax) signalMax = s;
      if (s < signalMin) signalMin = s;
    }
  }

  unsigned int peakToPeak = signalMax - signalMin;
  float currentSPL = SPL_MIN_DB +
                     (float)peakToPeak / 4095.0f * (SPL_MAX_DB - SPL_MIN_DB);

  if (firstSample) {
    lmaxSPL    = currentSPL;
    lminSPL    = currentSPL;
    firstSample = false;
  } else {
    if (currentSPL > lmaxSPL) lmaxSPL = currentSPL;
    if (currentSPL < lminSPL) lminSPL = currentSPL;
  }

  linearEnergySum += pow(10.0f, currentSPL / 10.0f);
  sampleCount++;

  if (sampleCount > 0)
    leqSPL = 10.0f * log10(linearEnergySum / (float)sampleCount);
}

// ─────────────────────────────────────────────────────────────
// resetSPLAccumulators — call after every publish cycle.
// ─────────────────────────────────────────────────────────────
void resetSPLAccumulators() {
  linearEnergySum = 0.0f;
  sampleCount     = 0;
  firstSample     = true;
  leqSPL  = 0.0f;
  lmaxSPL = 0.0f;
  lminSPL = 0.0f;
}

// ─────────────────────────────────────────────────────────────
// gaussRand — Box-Muller transform: N(0,1) from two uniform samples.
// Returns a single standard-normal random variate.
// ─────────────────────────────────────────────────────────────
static float gaussRand() {
  // Use esp_random() for a hardware-backed 32-bit uniform random.
  // Divide by UINT32_MAX to get u in (0, 1].
  float u1 = (float)(esp_random() & 0x7FFFFFFF) / 2147483647.0f + 1e-9f;
  float u2 = (float)(esp_random() & 0x7FFFFFFF) / 2147483647.0f;
  return sqrtf(-2.0f * logf(u1)) * cosf(2.0f * M_PI * u2);
}


// ─────────────────────────────────────────────────────────────
// generateSimulatedReadings
//
// Produces one set of realistic, unbalanced sensor readings for
// a given IoTDevice using its embedded SimProfile.
//
// Global environment baselines:
//   Leq floor  : 55 dB    (ambient restaurant hum)
//   Temp base  : 22.0 °C
//   Humidity   : 45.0 %
//   RSSI ref   : -45 dBm  (AP in the same room)
//
// Gaussian σ values chosen to match typical sensor variance:
//   SPL Leq σ  :  2.5 dB   (slow drift)
//   SPL Lmax σ :  4.0 dB   (sharper transients)
//   SPL Lmin σ :  1.5 dB   (floor is more stable)
//   Temp σ     :  0.4 °C
//   Humidity σ :  1.5 %
//   RSSI σ     :  3.0 dBm  (multipath fading)
// ─────────────────────────────────────────────────────────────
SimReading generateSimulatedReadings(const IoTDevice& device) {
  const SimProfile& p = device.profile;
  SimReading r;

  // ── Leq: floor + bias + Gaussian noise ────────────────────
  const float LEQ_FLOOR = 55.0f;
  r.leq  = LEQ_FLOOR + p.leqBias  + gaussRand() * 2.5f;

  // ── Lmax: always ≥ Leq; bias adds typical transient headroom
  r.lmax = r.leq + fabsf(p.lmaxBias + gaussRand() * 4.0f);
  if (r.lmax < r.leq) r.lmax = r.leq + 1.0f;

  // ── Lmin: always ≤ Leq; near-kitchen floor is raised by heat
  r.lmin = r.leq - fabsf(5.0f + gaussRand() * 1.5f);
  if (r.lmin > r.leq) r.lmin = r.leq - 1.0f;

  // ── Temperature ───────────────────────────────────────────
  const float TEMP_BASE = 22.0f;
  r.temp = TEMP_BASE + p.tempBias + gaussRand() * 0.4f;

  // ── Humidity ──────────────────────────────────────────────
  const float HUM_BASE = 45.0f;
  r.hum  = HUM_BASE  + p.humBias  + gaussRand() * 1.5f;
  r.hum  = constrain(r.hum, 0.0f, 100.0f);

  // ── RSSI ──────────────────────────────────────────────────
  const float RSSI_REF = -45.0f;
  r.rssi = RSSI_REF + p.rssiBias  + gaussRand() * 3.0f;
  r.rssi = constrain(r.rssi, -100.0f, -20.0f);

  return r;
}
