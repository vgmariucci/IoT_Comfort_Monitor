// Print an integer in "00" format (with leading zero),
// followed by a delimiter character to Serial.
// Input value assumed to be between 0 and 99.
void printI00(int val, char delim) {
    if (val < 10) Serial.print('0');
    Serial.print(val);
    if (delim > 0) Serial.print(delim);
}

// print time to Serial
void printTime(time_t t) {
    printI00(hour(t), ':');
    printI00(minute(t), ':');
    printI00(second(t), ' ');
}

// Convert reading_time value to be presented on the OLED Display
String convertDateTimeToOledDisplay(String reading_time) {

  String year = reading_time.substring(0, 4);
  String month = reading_time.substring(5, 7);
  String day = reading_time.substring(8, 10);
  String hour = reading_time.substring(11, 13);
  String minute = reading_time.substring(14, 16);
  
  return day + "/" + month + "/" + year + " " + hour + ":" + minute;
}

String getDS3231DateTime(){
    
    time_t t = myRTC.get();  // Retrieve time from DS3231
    tmElements_t tm;
    breakTime(t, tm);  // Break down time_t to tmElements_t

    // Populate a standard `tm` struct from `tmElements_t`
    struct tm timeinfo;
    timeinfo.tm_year = tm.Year + 1970 - 1900;  // Year since 1900
    timeinfo.tm_mon = tm.Month - 1;            // Month (0-11)
    timeinfo.tm_mday = tm.Day;                 // Day of the month (1-31)
    timeinfo.tm_hour = tm.Hour;                // Hour (0-23)
    timeinfo.tm_min = tm.Minute;               // Minute (0-59)
    timeinfo.tm_sec = tm.Second;               // Second (0-59)

    char timeDateComplete[50];
    strftime(timeDateComplete, 50, "%Y.%m.%dT%H:%M:%S", &timeinfo);
    String completeDateTimeDS3231RTC = timeDateComplete;

    return completeDateTimeDS3231RTC;
}

// Adjust and print the date and time of the DS3231 RTC module
void setDS3231DateTime(uint8_t yearNTP, uint8_t monthNTP, uint8_t dayNTP, uint8_t hoursNTP, uint8_t minutesNTP, uint8_t secondsNTP) {
    
    time_t t;
    tmElements_t tm;

    if (yearNTP >= 1000)
        tm.Year = CalendarYrToTm(yearNTP);
    else // (y < 100)
        tm.Year = y2kYearToTm(yearNTP);

    tm.Month = monthNTP;
    tm.Day = dayNTP;
    tm.Hour = hoursNTP;
    tm.Minute = minutesNTP;
    tm.Second = secondsNTP;
    
    t = makeTime(tm);
    myRTC.set(t); // use the time_t value to ensure correct weekday is set
    setTime(t);
    Serial.println();
    Serial.println("Adjusted DS3231 RTC values: ");
    
}

// Function to get date and time from NTPClient
String getNTPClientDateTimeAndSetDS3231RTC(){
    
    struct tm timeinfo;

    if (!getLocalTime(&timeinfo)) {
        return "Failed to obtain time";
    }

    char timeDateComplete[50];
    strftime(timeDateComplete, 50, "%Y.%m.%dT%H:%M:%S", &timeinfo);
    String completeDatetimeNTP = timeDateComplete;

    // Convert to appropriate values and call setDS3231DateTime
    uint8_t year     = timeinfo.tm_year + 1900 - 2000;
    uint8_t month    = timeinfo.tm_mon + 1;
    uint8_t monthDay = timeinfo.tm_mday;
    uint8_t hours    = timeinfo.tm_hour;
    uint8_t minutes  = timeinfo.tm_min;
    uint8_t seconds  = timeinfo.tm_sec;

    setDS3231DateTime(year, month, monthDay, hours, minutes, seconds);

    return completeDatetimeNTP;
}
