void connectToWifi() {

  WiFi.begin();
  
  // clear display
  display.clearDisplay(); 
  display.setTextSize(1);
  display.setCursor(10,10);
  display.print("Attempting");
  display.setCursor(10,30);
  display.print("to connect");
  display.setCursor(10,50);
  display.print("to the Internet...");
  display.display();

  delay(3000);

  if (WiFi.status() != WL_CONNECTED && WIFI_CONNECTION_CHECK_TIMEOUT < 5){

  // clear display
  display.clearDisplay(); 
  display.setTextSize(1);
  display.setCursor(10,10);
  display.print("Can't connect");
  display.setCursor(10,30);
  display.print("to the Internet");
  display.display();
  delay(3000);
  
  WIFI_CONNECTION_CHECK_TIMEOUT += 1;
  if(WIFI_CONNECTION_CHECK_TIMEOUT >=5) WIFI_CONNECTION_CHECK_TIMEOUT = 5;

  }
  if(WiFi.status() == WL_CONNECTED){
  
    // Init and get the time
    configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
    
    // clear display
    display.clearDisplay(); 
    // display temperature
    display.setTextSize(1);
    display.setCursor(30,30);
    display.print("WiFi CONNECTED");
    display.display();
    delay(3000);
  }
}
  
void configure_WiFi(){

  // WiFiManager
  // Local intialization. Once its business is done, there is no need to keep it around
  WiFiManager wifiManager;

  // Uncomment and run it once, if you want to erase all the stored information
  wifiManager.resetSettings();
  
  // fetches ssid and pass from eeprom and tries to connect
  // if it does not connect it starts an access point with the specified name
  // here  "Ambi IoT Device"
  // and goes into a blocking loop awaiting configuration
  
  wifiManager.autoConnect("Ambi IoT Device");
}

