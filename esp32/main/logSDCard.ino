// ============================================================
// logSDCard.ino
// Writes the PHYSICAL ESP32 unit's sensor readings to the SD card.
// CSV columns:
//   reading_time ; customer_ID ; device_id ;
//   temperature ; humidity ; leq_spl ; lmax_spl ; lmin_spl ; wifi_rssi
//
// Note: the SD log reflects the real hardware sensors of this
// single ESP32 unit. Simulated per-device data goes via MQTT only.
// ============================================================

#define ESP32_DEVICE_ID "001Corp20250122"

void flushBufferToSD() {
  Serial.println("[SD] Flushing buffer...");
  for (const String& msg : dataBuffer) {
    appendFile(SD, "/data.txt", msg.c_str());
  }
  dataBuffer.clear();
  Serial.println("[SD] Buffer flushed and cleared.");
}

void logSDCard() {
  // wifiRSSI == 1 is the "not connected" sentinel
  String rssiStr = (wifiRSSI == 1) ? "NC" : String(wifiRSSI);

  dataMessage =
    reading_time           + ";" +
    customer_ID            + ";" +
    ESP32_DEVICE_ID        + ";" +
    String(temperature, 2) + ";" +
    String(humidity, 2)    + ";" +
    String(leqSPL,  2)     + ";" +
    String(lmaxSPL, 2)     + ";" +
    String(lminSPL, 2)     + ";" +
    rssiStr                +
    "\r\n";

  dataBuffer.push_back(dataMessage);
  Serial.println("[SD] Buffered: " + dataMessage);

  if (dataBuffer.size() >= BUFFER_SIZE) flushBufferToSD();
}

void writeFile(fs::FS& fs, const char* path, const char* message) {
  File file = fs.open(path, FILE_WRITE);
  if (!file) { Serial.println("[SD] writeFile: open failed"); return; }
  file.print(message) ? Serial.println("[SD] Write OK")
                      : Serial.println("[SD] Write FAIL");
  file.close();
}

void appendFile(fs::FS& fs, const char* path, const char* message) {
  File file = fs.open(path, FILE_APPEND);
  if (!file) { Serial.println("[SD] appendFile: open failed"); return; }
  file.print(message) ? Serial.println("[SD] Append OK")
                      : Serial.println("[SD] Append FAIL");
  file.close();
}
