// ============================================================
// main.ino — ESP32 Multi-Device IoT Monitor
//
// Sensor pipeline (physical):
//   • DHT22      → temperature, humidity   (physical ESP32 unit)
//   • MCP602 mic → leqSPL / lmaxSPL / lminSPL (physical unit)
//   • WiFi.RSSI  → wifiRSSI  (physical signal strength in dBm)
//
// Simulation:
//   • generateSimulatedReadings(device) → per-device unbalanced
//     values sent via MQTT to each Konker topic.
//
// OLED state machine (5 states, cycled by DISPLAY_BTN):
//   State 0 — Numeric: Sound (Leq / Lmax / Lmin)
//   State 1 — Numeric: Temperature & Humidity
//   State 2 — Chart:   Leq over time
//   State 3 — Chart:   Temperature & Humidity over time (dual Y)
//   State 4 — Chart:   Wi-Fi RSSI over time (dBm)
//
// Publish interval: 10 s to all simulated Konker devices.
// ============================================================

// ── Pin definitions ───────────────────────────────────────────
#define I2C_SDA 21
#define I2C_SCL 22

#define SCREEN_WIDTH        128
#define SCREEN_HEIGHT        64
#define OLED_SD1306_ADDRESS 0x3C

#define DHTPIN  4
#define DHTTYPE DHT22

#define LED         2
#define DISPLAY_BTN 26
#define WIFI_BTN    25
#define SD_CS        5
#define AUDIO_PIN   34

// ── Libraries ────────────────────────────────────────────────
#include <WiFi.h>
#include <PubSubClient.h>
#include <WiFiClient.h>
#include <WebServer.h>
#include <WiFiManager.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <SPI.h>
#include "FS.h"
#include "SD.h"
#include "time.h"
#include <WiFiUdp.h>
#include <DHT.h>
#include <DS3232RTC.h>
#include "Secrets.h"
#include <vector>
#include <math.h>
#include <esp_random.h>   // hardware RNG for gaussRand()

// ── Publish timing ────────────────────────────────────────────
unsigned long lastPublishTime    = 0;
const unsigned long PUBLISH_INTERVAL = 900000; // ms

// ── Physical sensor readings ──────────────────────────────────
// Written by readTemperatureAndHumidityFromDHT22() and processAudioSPL().
// These reflect the single real ESP32 unit in the field.
float temperature = 0.0f;
float humidity    = 0.0f;

float leqSPL  = 0.0f;   // IEC 61672 Leq (energy average)
float lmaxSPL = 0.0f;   // True peak within window
float lminSPL = 0.0f;   // Noise floor within window

// Physical Wi-Fi signal strength of this ESP32 unit (dBm).
// Updated once per publish cycle. Range: -20 (excellent) … -100 (dead).
// Sentinel value 1 means "not connected".
int32_t wifiRSSI = 1;

// ── Chart history ring buffers ────────────────────────────────
// One snapshot per publish cycle (10 s cadence).
// Displayed in OLED states 2, 3 and 4.
#define CHART_POINTS 20

float   splHistory [CHART_POINTS];
float   tempHistory[CHART_POINTS];
float   humHistory [CHART_POINTS];
float   rssiHistory[CHART_POINTS];  // stored as float for chart maths

uint8_t historyIndex  = 0;
bool    historyFilled = false;      // true once the ring wraps around

// ── OLED / UI state ───────────────────────────────────────────
uint8_t oledState      = 0;        // 0-4, cycled by DISPLAY_BTN
bool    AP_mode_status = false;
bool    statusLED      = false;

unsigned int WIFI_CONNECTION_CHECK_TIMEOUT = 0;

// ── Time / RTC ────────────────────────────────────────────────
String reading_time;
String dataMessage;

const char* ntpServer          = "pool.ntp.org";
const long  gmtOffset_sec      = -4 * 3600;
const int   daylightOffset_sec =  3600;

bool setDateTimeDS3231RTCUsingNTPClientFlag = false;

// ── SD write buffer ───────────────────────────────────────────
std::vector<String> dataBuffer;
std::vector<String> mqttDataBuffer;
const size_t BUFFER_SIZE = 15;

// ── Object instantiations ─────────────────────────────────────
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);
DS3232RTC        myRTC;
DHT              dht(DHTPIN, DHTTYPE);
WiFiClient       espClient;
PubSubClient     mqttClient(espClient);

// ─────────────────────────────────────────────────────────────
void setup() {
  pinMode(LED,         OUTPUT);
  pinMode(DISPLAY_BTN, INPUT_PULLUP);
  pinMode(WIFI_BTN,    INPUT_PULLUP);
  pinMode(AUDIO_PIN,   INPUT);

  Serial.begin(115200);

  memset(splHistory,  0, sizeof(splHistory));
  memset(tempHistory, 0, sizeof(tempHistory));
  memset(humHistory,  0, sizeof(humHistory));
  memset(rssiHistory, 0, sizeof(rssiHistory));

  startOLEDDisplay();
  connectToWifi();
  myRTC.begin();
  startSDCard();
  dht.begin();                                                  // Initialize DHT22 sensor

  mqttClient.setBufferSize(512);
  lastPublishTime = millis();
}

// ─────────────────────────────────────────────────────────────
void loop() {

  refreshKeyboardReadings();

  // One-time RTC sync after Wi-Fi connects
  if (WiFi.status() == WL_CONNECTED && !setDateTimeDS3231RTCUsingNTPClientFlag) {
    getNTPClientDateTimeAndSetDS3231RTC();
    setDateTimeDS3231RTCUsingNTPClientFlag = true;
  }

  // Continuously sample real sensors so SPL accumulates between publishes
  reading_time = getDS3231DateTime();
  processAudioSPL();
  readTemperatureAndHumidityFromDHT22();
  displayOledData();

  // ── Publish cycle ─────────────────────────────────────────
  unsigned long now = millis();
  if (now - lastPublishTime >= PUBLISH_INTERVAL) {
    lastPublishTime = now;

    // Read physical Wi-Fi RSSI for the chart history
    wifiRSSI = (WiFi.status() == WL_CONNECTED) ? (int32_t)WiFi.RSSI() : 1;

    // Push physical readings into the chart ring buffer
    pushHistory(leqSPL, temperature, humidity, (float)wifiRSSI);

    // SD log uses physical readings
    logSDCard();

    // MQTT: per-device simulated readings
    if (WiFi.status() == WL_CONNECTED) {
      sendDataForAllDevices();
    } else {
      Serial.println("[MQTT] Wi-Fi not connected — skipping publish.");
    }

    resetSPLAccumulators();
  }
}

// ─────────────────────────────────────────────────────────────
// pushHistory — append one snapshot to all ring buffers
// ─────────────────────────────────────────────────────────────
void pushHistory(float spl, float temp, float hum, float rssi) {
  splHistory [historyIndex] = spl;
  tempHistory[historyIndex] = temp;
  humHistory [historyIndex] = hum;
  rssiHistory[historyIndex] = rssi;

  historyIndex = (historyIndex + 1) % CHART_POINTS;
  if (historyIndex == 0) historyFilled = true;
}
