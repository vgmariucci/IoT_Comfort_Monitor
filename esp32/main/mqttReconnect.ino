// ============================================================
// mqttReconnect.ino
// Connects (or reconnects) the MQTT client using the credentials
// of a specific simulated device.
// ============================================================

// mqttReconnect — attempts to connect using the given device credentials.
// Returns true if connected, false if all retries fail.
bool mqttReconnect(const IoTDevice& device) {
  const uint8_t MAX_RETRIES = 3;
  uint8_t attempts = 0;

  // Use the Konker username as the MQTT client ID to keep it unique
  // per device and avoid broker-side session conflicts.
  String clientId = String("ESP32_") + device.mqttUser;

  while (!mqttClient.connected() && attempts < MAX_RETRIES) {
    Serial.printf("[MQTT] Connecting as '%s' (%s)...\n",
                  clientId.c_str(), device.location);

    if (mqttClient.connect(clientId.c_str(), device.mqttUser, device.mqttPass)) {
      Serial.printf("[MQTT] Connected to broker for device: %s\n", device.location);
      return true;
    }

    Serial.printf("[MQTT] Failed (rc=%d). Retry %d/%d in 2 s...\n",
                  mqttClient.state(), attempts + 1, MAX_RETRIES);
    delay(2000);
    attempts++;
  }

  Serial.printf("[MQTT] Could not connect for device: %s — skipping.\n",
                device.location);
  return false;
}
