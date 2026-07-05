// ============================================================
//
// Rename this file to "Secrets.h"
//
// Credentials, Device Configuration & Simulation Profiles
//
// ============================================================

// Customer identification
String customer_ID = "Customer_ID";

// MQTT Broker Settings — Konker IoT Platform
#define MQTT_SERVER "mqtt_server_address"
#define MQTT_PORT   1883

// ============================================================
// SimProfile — per-device environmental bias for simulation.
//
// Each device gets a unique "personality" that reflects its
// real-world placement inside a restaurant:
//
//   leqBias   (dB)   — baseline Leq offset above the global floor
//   lmaxBias  (dB)   — how much louder the spikes tend to be
//   tempBias  (°C)   — temperature offset (kitchen = warmer)
//   humBias   (%)    — relative humidity offset
//   rssiBias  (dBm)  — Wi-Fi signal offset (negative = weaker)
//
// Gaussian noise is added on top of each bias every cycle so
// readings look like real sensor data, not flat lines.
// ============================================================
struct SimProfile {
  float leqBias;   // dB  above the global quiet floor (~55 dB)
  float lmaxBias;  // dB  above leq (transient peaks)
  float tempBias;  // °C  above baseline ambient (~22 °C)
  float humBias;   // %   above baseline humidity (~45 %)
  float rssiBias;  // dBm relative to AP reference (-45 dBm)
};

// ============================================================
// IoTDevice — MQTT credentials + location label + sim profile
// ============================================================
struct IoTDevice {
  const char* location;    // Friendly name
  const char* mqttUser;    // Konker username
  const char* mqttPass;    // Konker password
  const char* mqttTopic;   // Konker pub topic
  SimProfile  profile;     // Simulation bias for this location
};


// ─────────────────────────────────────────────────────────────
// SimReading — output struct filled by generateSimulatedReadings()
// ─────────────────────────────────────────────────────────────
struct SimReading {
  float leq;       // dB
  float lmax;      // dB
  float lmin;      // dB
  float temp;      // °C
  float hum;       // %
  float rssi;      // dBm (stored as float for history; cast to int32 for MQTT)
};

// ============================================================
// Simulated device array
//
// Restaurant layout rationale:
//
// "Lunch Area"       — busy tables, moderate noise, normal temp,
//                      best Wi-Fi (closest to router).
// "Entrance"         — intermittent door noise, cooler, decent Wi-Fi
//                      but signal drops when glass door opens.
// "Close to Kitchen" — loudest (equipment + staff), hottest,
//                      most humid, worst Wi-Fi (steel appliances).
// "Close to Restrooms"— quietest, slightly cooler, mid Wi-Fi.
// ============================================================
const IoTDevice simulatedDevices[] = {
  {
    "Lunch Area",
    "lunch_area_device_user",
    "lunch_area_device_pass",
    "data/lunch_area_device_user/pub/lunch_area",
    { 12.0f,  8.0f,  1.5f,  3.0f,   0.0f }  // moderate noise, best signal
  },
  {
    "Entrance",
    "entrance_device_user",
    "entrance_device_pass",
    "data/entrance_device_user/pub/entrance",
    {  8.0f, 14.0f, -1.0f, -5.0f, -15.0f }  // door slams → high Lmax, cooler
  },
  {
    "Close to Kitchen",
    "close_to_kitchen_device_user",
    "close_to_kitchen_device_pass",
    "data/close_to_kitchen_device_user/pub/close_to_kitchen",
    { 22.0f, 10.0f,  8.0f, 18.0f, -28.0f }  // loudest, hottest, worst Wi-Fi
  },
  {
    "Close to Restrooms",
    "close_to_restrooms_device_user",
    "close_to_restrooms_device_pass",
    "data/close_to_restrooms_device_user/pub/close_to_restrooms",
    {  3.0f,  5.0f, -0.5f,  6.0f, -12.0f }  // quietest, slightly humid
  },
  {
    "External Area",
    "external_area_device_user",
    "external_area_device_pass",
    "data/external_area_device_user/pub/external_area",
    {  15.0f,  3.0f, -0.5f,  6.0f, -15.0f }  // loud, humid, good Wi-Fi
  }
};

const int NUM_SIMULATED_DEVICES =
    sizeof(simulatedDevices) / sizeof(simulatedDevices[0]);
