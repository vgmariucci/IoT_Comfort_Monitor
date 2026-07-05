void startSDCard(){

  // Initialize SD card
  SD.begin(SD_CS);  
  if(!SD.begin(SD_CS)) {
    Serial.println("Card Mount Failed");

    // clear display
   display.clearDisplay(); 
   // display temperature
   display.setTextSize(1);
   display.setCursor(20,20);
   display.print("Card Mount");
   display.setCursor(20,40);
   display.print("Failed");
   display.display();
   delay(2000);
    
    return;
  }

  uint8_t cardType = SD.cardType();
  
  if(cardType == CARD_NONE) {
    Serial.println("No SD card attached");

     // clear display
   display.clearDisplay(); 
   // display temperature
   display.setTextSize(1);
   display.setCursor(20,20);
   display.print("No SD card");
   display.setCursor(20,40);
   display.print("attached");
   display.display();
   delay(2000);
   
    return;
  }
  Serial.println("Initializing SD card...");
  if (!SD.begin(SD_CS)) {
    Serial.println("ERROR - SD card initialization failed!");

     // clear display
   display.clearDisplay(); 
   // display temperature
   display.setTextSize(1);
   display.setCursor(20,20);
   display.print("SD card");
   display.setCursor(20,40);
   display.print("initialization failed!");
   display.display();
   delay(2000);
   
    return;    // init failed
  }

  // If the data.txt file doesn't exist
  // Create a file on the SD card and write the data labels
  File file = SD.open("/data.txt");

 if(!file) {

   // If the data.txt file doesn't exist
  // Create a file on the SD card and write the data labels
  File file = SD.open("/data.txt");
  

  Serial.println("Creating files...");

   // clear display
   display.clearDisplay(); 
   // display temperature
   display.setTextSize(1);
   display.setCursor(20,20);
   display.print("File data.txt");
   display.setCursor(20,40);
   display.print("does not exist");
   display.display();
   delay(2000);

   // clear display
   display.clearDisplay(); 
   // display temperature
   display.setTextSize(1);
   display.setCursor(20,20);
   display.print("Creating file");
   display.setCursor(20,40);
   display.print("data.txt");
   display.display();
   delay(2000);
 
   writeFile(
              SD, 
              "/data.txt",                                             // Put the filename to be created in this row
              
              // Additional information considered relevant to the customer
              
              "ESP DATA IoT\r\n"                                        // The responsible for monitoring the below parameters
              "CUSTOMER: PUT_HERE_THE_CUSTOMER_NAME\r\n"                // Customer's name
              "SECTOR: PUT_HERE_THE_SECTOR_NAME\r\n"                    // Some specific sector's name
              "LOCAL: PUT_HERE_THE_LOCAL_NAME\r\n\r\n"                  // The local name or geolocation reference
              
              // The set of paremeters to be registered into rows of data.txt file
              
              "timestamp;"                                               // The timestamp regarding the measured values 
              "customer_ID;"                          // The customer ID, idealy the customer's name or identificaion
              "iot_device_serial_number;"             // The serial number of the ESP32 circuit connected with the IMS or other power meters
              "temperature;"                                             // The DHT22 temperature reading
              "humidity;"                                                // The DHT22 humidity reading
              "avg_spl;"                                                // The average SPL measure
              "max_spl;"                                                // The max SPL measure
              "wifi_status\r\n"                                          // The WiFi status (connected 1 or disconnected 0)
              );
  }
  else {

    Serial.println("File already exists"); 
    // clear display
    display.clearDisplay(); 
    display.setTextSize(1);
    display.setCursor(20,20);
    display.print("File data.txt");
    display.setCursor(20,40);
    display.print("already exists");
    display.display();
    delay(1000);
 
  }
  file.close();
}
