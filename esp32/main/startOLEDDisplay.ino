void startOLEDDisplay() {
  
  // Initialize I2C with custom pins
  Wire.begin(I2C_SDA, I2C_SCL);

  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_SD1306_ADDRESS)) {
    Serial.println(F("SSD1306 allocation failed"));
    for (;;)
      ;
  }
  delay(2000);

  display.setRotation(2);
  display.clearDisplay();
  display.setTextColor(WHITE);
}
