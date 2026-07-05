// ============================================================
// sendDataViaMQTT.ino
//
// For each simulated IoTDevice:
//   1. Generate a set of unbalanced readings via
//      generateSimulatedReadings() (processAudioSPL.ino).
//   2. Reconnect the MQTT client with the device's credentials.
//   3. Publish one JSON payload to the device's Konker topic.
//
// The MQTT payload contains both simulated environmental data
// (leq, lmax, lmin, temp, hum) AND the per-device simulated
// RSSI, so the Konker platform and downstream apps see realistic,
// location-specific signal quality for each monitor point.
// ============================================================

// buildPayload — JSON string for a given device + SimReading
String buildPayload(const IoTDevice& device, const SimReading& r) {
  return String("{")
    + "\"reading_time\":\""    + reading_time          + "\","
    + "\"customer_ID\":\""     + customer_ID           + "\","
    + "\"device_location\":\"" + device.location       + "\","
    + "\"temperature\":"       + String(r.temp,  2)    + ","
    + "\"humidity\":"          + String(r.hum,   2)    + ","
    + "\"leq_spl\":"           + String(r.leq,   2)    + ","
    + "\"lmax_spl\":"          + String(r.lmax,  2)    + ","
    + "\"lmin_spl\":"          + String(r.lmin,  2)    + ","
    + "\"wifi_rssi\":"         + String((int32_t)r.rssi)
    + "}";
}

// sendDataForAllDevices — iterate, simulate, reconnect, publish
void sendDataForAllDevices() {
  mqttClient.setServer(MQTT_SERVER, MQTT_PORT);

  for (int i = 0; i < NUM_SIMULATED_DEVICES; i++) {
    const IoTDevice& device = simulatedDevices[i];

    // Generate this device's unique simulated readings
    SimReading reading = generateSimulatedReadings(device);

    Serial.printf("\n[SIM] %s → Leq=%.1f Lmax=%.1f Lmin=%.1f "
                  "T=%.1f H=%.1f RSSI=%d\n",
                  device.location,
                  reading.leq, reading.lmax, reading.lmin,
                  reading.temp, reading.hum, (int32_t)reading.rssi);

    if (mqttClient.connected()) {
      mqttClient.disconnect();
      delay(100);
    }

    if (!mqttReconnect(device)) continue;

    mqttClient.loop();

    String payload = buildPayload(device, reading);
    Serial.printf("[MQTT] Topic  : %s\n", device.mqttTopic);
    Serial.printf("[MQTT] Payload: %s\n", payload.c_str());

    if (mqttClient.publish(device.mqttTopic, payload.c_str())) {
      Serial.printf("[MQTT] OK — %s\n", device.location);
      statusLED = !statusLED;
      digitalWrite(LED, statusLED);
    } else {
      Serial.printf("[MQTT] FAIL — %s\n", device.location);
    }

    mqttClient.disconnect();
    delay(200);
  }

  statusLED = false;
  digitalWrite(LED, LOW);
}
