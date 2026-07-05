# Ambi — IoT Comfort Monitor

Final project for the "IoT e Android Embarcado" course at Unicamp.

A full-stack IoT system that monitors acoustic and environmental comfort
parameters in a restaurant, combining an ESP32 sensor node with an
Android dashboard.

| Component | Description |
|---|---|
| [`android/`](android/README.md) | Kotlin/Jetpack Compose Android app — Konker IoT dashboard |
| [`esp32/`](esp32/main/README.md) | ESP32 Arduino firmware — SPL measurement + MQTT publishing |

### PoC Hardware Setup

![ESP32_Sensors_and_Modules_Setup](imgs/01_ESP32_Sensors_and_Modules_Setup.jpeg)

### Product Design Projection and Simple Solution Architecture (MQTT/HTTP/OAuth2)

![Portable_IoT_Comfort_Module_Monitor](imgs/02_Portable_IoT_Comfort_Module_Monitor.png)

### Konker IoT Platform Setup

![Konker_IoT_Devices](imgs/03_Konker_IoT_Devices.png)

![Konker_IoT_Device_Payload](imgs/04_Konker_IoT_Lunch_Area_Device.png)


### Ambi Comfort Monitor Android App

![Ambi_App_Icon](imgs/05_Ambi_App_Icon.png)

![Ambi_App_Login_Screen](imgs/06_Ambi_App_Login_Screen.png)

![Ambi_App_Dashboard_View_All_Zones_Heatmap](imgs/07_Ambi_App_Dashboard_View_All_Zones_Heatmap.png)

![Ambi_App_Dashboard_Lunch_Area_Heatmap](imgs/08_Ambi_App_Dashboard_Lunch_Area_Heatmap.png)

![Ambi_App_Dashboard_Lunch_Area_Line_Charts](imgs/09_Ambi_App_Dashboard_Lunch_Area_Line_Charts.png)

![Ambi_App_Floor_Plan_View](imgs/10_Ambi_App_Floor_Plan_View.png)