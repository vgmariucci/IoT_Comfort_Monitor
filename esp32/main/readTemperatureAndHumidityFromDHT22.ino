void readTemperatureAndHumidityFromDHT22(){

  // Reading temperature or humidity takes about 250 ms
  // Sensor readings require at least 2 seconds
  humidity = dht.readHumidity();
  temperature = dht.readTemperature();
  
  // Check if any readings failed (to try again)
  if(isnan(humidity) || isnan(temperature)){
    Serial.println("Failed to read from DHT sensor!");
    return;
  }
  
}