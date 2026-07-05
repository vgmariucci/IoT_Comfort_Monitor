# Ambi IoT — ESP32 Sound & Environment Monitor

> **Final Project — "IoT e Android Embarcado" Course · Unicamp**

Firmware for the ESP32-based sensor node that forms the IoT backbone of the **Ambi** comfort-monitoring system. The device measures acoustic noise (SPL / Leq), temperature, and humidity in a restaurant environment, and simultaneously simulates five virtual sensor locations — each with a realistic, location-differentiated profile — publishing all data to the **Konker IoT platform** via MQTT.

---

## Table of Contents

1. [Overview](#overview)
2. [Hardware & Pin Map](#hardware--pin-map)
3. [Library Dependencies](#library-dependencies)
4. [Project Structure](#project-structure)
5. [Core Features](#core-features)
6. [SPL Measurement Algorithm (IEC 61672 Leq)](#spl-measurement-algorithm-iec-61672-leq)
7. [Gaussian Simulation Engine](#gaussian-simulation-engine)
8. [Simulated Device Profiles](#simulated-device-profiles)
9. [OLED Display State Machine](#oled-display-state-machine)
10. [MQTT Publishing](#mqtt-publishing)
11. [SD Card Logging](#sd-card-logging)
12. [Wi-Fi & Captive Portal Setup](#wi-fi--captive-portal-setup)
13. [RTC & NTP Time Sync](#rtc--ntp-time-sync)
14. [JSON Payload Format](#json-payload-format)
15. [Getting Started](#getting-started)
16. [Configuration Reference](#configuration-reference)

---

## Overview

| Item | Detail |
|---|---|
| MCU | ESP32 (dual-core, 240 MHz) |
| Platform | Arduino / ESP-IDF via Arduino Core |
| IoT Backend | Konker Platform (MQTT broker: `ucmp.soneca.dev:1883`) |
| Sensors | Electret microphone + MCP602 pre-amp (ADC), DHT22 (Temp/Hum) |
| Display | SSD1306 OLED 128×64 (I²C) |
| RTC | DS3232 (I²C) with NTP one-shot sync |
| Storage | MicroSD (SPI) — CSV data log |
| Connectivity | Wi-Fi 802.11 b/g/n; WiFiManager captive portal for provisioning |
| Publish interval | 15 minutes (configurable via `PUBLISH_INTERVAL`) |
| Virtual locations | 5 (Lunch Area, Entrance, Close to Kitchen, Close to Restrooms, External Area) |

---

## Hardware & Pin Map

**Non-pinned components:**
- **MCU:** ESP32 DevKit (38-pin, dual-core 240 MHz)
- **OLED Display:** SSD1306, 128×64, I²C address `0x3C`
- **RTC:** DS3232 (DS3231-compatible), I²C — shares bus with OLED

| GPIO | Function | Component | Electrical Notes |
|---|---|---|---|
| 34 | Microphone ADC | Electret capsule + MCP602 op-amp pre-amplifier | Input-only pin; no pull-up required |
| 4 | DHT22 data | DHT22 temperature/humidity sensor | 10 kΩ pull-up to 3.3 V |
| 21 | I²C SDA | Shared: SSD1306 OLED + DS3232 RTC | — |
| 22 | I²C SCL | Shared: SSD1306 OLED + DS3232 RTC | — |
| 5 | SD card CS (SPI) | MicroSD module | Chip-select |
| 2 | Status LED | Built-in LED | Active HIGH; blinks during publish cycle |
| 25 | Wi-Fi button | Tactile push-button | Active LOW, debounced 50 ms — triggers captive portal |
| 26 | Display button | Tactile push-button | Active LOW, debounced 50 ms — cycles OLED state |

---

## Library Dependencies

Install all libraries via the Arduino Library Manager or PlatformIO:

| Library | Version | Purpose |
|---|---|---|
| Arduino ESP32 Core | ≥ 2.0 | ESP32 hardware abstraction, `esp_random()` |
| DHT sensor library | ≥ 1.4 | DHT22 temperature/humidity driver |
| Adafruit SSD1306 | ≥ 2.5 | OLED display driver |
| Adafruit GFX | ≥ 1.11 | Graphics primitives for OLED |
| PubSubClient | ≥ 2.8 | MQTT client |
| WiFiManager | ≥ 2.0 | Captive portal for Wi-Fi provisioning |
| RTClib | ≥ 2.1 | DS3232/DS3231 RTC interface |
| SD (built-in) | — | MicroSD card file I/O |
| NTPClient | ≥ 3.2 | NTP time fetch for one-shot RTC sync |
| Wire (built-in) | — | I²C bus for OLED + RTC |

---

## Project Structure

```
main/
├── main.ino                    # Entry point: setup(), loop(), ring-buffer globals
├── Secrets_Example.h           # ⚠ Rename to Secrets.h — credentials + sim profiles
├── processAudioSPL.ino         # SPL measurement (IEC 61672 Leq) + Gaussian simulator
├── sendDataViaMQTT.ino         # JSON payload builder + per-device MQTT publisher
├── displayOledData.ino         # 5-state OLED state machine (3 numeric + 2 chart screens)
├── connectToWiFi.ino           # WiFiManager captive-portal setup
├── getSetReadRTCValues.ino     # DS3232 RTC driver + NTP one-shot sync
├── logSDCard.ino               # Buffered CSV logger (buffer size = 15 records)
├── mqttReconnect.ino           # Per-device MQTT connect with 3-retry logic
├── refreshKeyboardReadings.ino # Debounced button handlers (50 ms)
├── startOLEDDisplay.ino        # I²C + SSD1306 initialisation
└── startSDCard.ino             # SD mount, type check, header file creation
```

All tuneable constants (`PUBLISH_INTERVAL`, `CHART_POINTS`, pin assignments, etc.) and their default values are listed in the [Configuration Reference](#configuration-reference) section. Four circular ring buffers (`splHistory`, `tempHistory`, `humHistory`, `rssiHistory`, depth = `CHART_POINTS`) are updated each publish cycle and read by the OLED sparkline screens — see [Core Features](#core-features) for the update flow.

---

## Core Features

### Main Loop Sequence

Each iteration of `loop()` executes in order:

1. `refreshKeyboardReadings()` — check buttons; update OLED state or queue captive portal
2. NTP sync check — calls `getNTPClientDateTimeAndSetDS3231RTC()` once after the first Wi-Fi connection
3. `processAudioSPL()` — 50 ms ADC window; accumulates energy sum for Leq calculation
4. `readTemperatureAndHumidityFromDHT22()` — reads DHT22; updates globals
5. `displayOledData()` — renders the active OLED screen
6. Publish check — if `millis() - lastPublishTime >= PUBLISH_INTERVAL`, runs the full publish cycle:
   - Calls `generateSimulatedReadings()` for each of the 5 virtual devices
   - Calls `sendDataViaMQTT()` per device (credential rotation, reconnect, publish, disconnect)
   - Appends one row to SD log via `logSDCard()`
   - Updates ring buffers and resets Leq accumulators

---

## SPL Measurement Algorithm (IEC 61672 Leq)

The firmware implements an energy-averaging algorithm consistent with the IEC 61672-1 standard for equivalent continuous sound level (Leq).

### Sampling Window

Every call to `processAudioSPL()` opens a **50 ms ADC window**. During this window the firmware continuously reads the microphone ADC (GPIO 34) and tracks the peak-to-peak amplitude of the waveform.

### SPL Mapping

The peak-to-peak amplitude (0–4095 ADC counts) is linearly mapped to a decibel range:

```
SPL = SPL_MIN_DB + (peakToPeak / 4095) × (SPL_MAX_DB − SPL_MIN_DB)
```

where `SPL_MIN_DB = 30 dB` and `SPL_MAX_DB = 120 dB`, covering the full audible dynamic range of the electret capsule.

### Leq Energy Averaging

Rather than averaging SPL values directly (which would underestimate loud events), the firmware accumulates **linear acoustic energy**:

```
linearEnergySum += 10^(SPL / 10)
sampleCount++
```

At publish time the equivalent continuous level is calculated as:

```
Leq = 10 × log₁₀(linearEnergySum / sampleCount)
```

This is mathematically equivalent to the IEC 61672 time-weighted energy average over the measurement period.

### Lmax and Lmin

Running maximum and minimum SPL values are tracked over the same accumulation period and reset together with the Leq accumulators after each publish cycle.

---

## Gaussian Simulation Engine

Because a single physical ESP32 represents all five restaurant zones, the firmware includes a Box-Muller Gaussian noise generator seeded from the ESP32's hardware random number generator (`esp_random()`). This produces statistically realistic, naturally varying sensor readings for each virtual device.

### Box-Muller Transform

```cpp
static float gaussRand() {
    float u1 = (float)(esp_random() & 0x7FFFFFFF) / 2147483647.0f + 1e-9f;
    float u2 = (float)(esp_random() & 0x7FFFFFFF) / 2147483647.0f;
    return sqrtf(-2.0f * logf(u1)) * cosf(2.0f * M_PI * u2);
}
```

`u1` and `u2` are independent uniform [0,1] values derived from 32-bit hardware entropy. The transform produces a standard-normal (µ=0, σ=1) variate, which is then scaled by a per-parameter sigma and offset by the device's bias.

### Simulation Baselines and Noise Parameters

| Parameter | Baseline | Gaussian σ |
|---|---|---|
| Leq SPL | 55 dB (LEQ_FLOOR) | 2.5 dB |
| Lmax SPL | Leq + lmaxBias | 4.0 dB |
| Lmin SPL | Leq − 3 dB | 1.5 dB |
| Temperature | 22 °C | 0.4 °C |
| Relative Humidity | 45 % | 1.5 % |
| Wi-Fi RSSI | −45 dBm (RSSI_REF) | 3.0 dBm |

Each virtual device's `SimProfile` adds a deterministic bias on top of the baseline before Gaussian noise is applied, making the five locations feel distinct while still showing natural variation cycle-to-cycle.

---

## Simulated Device Profiles

Defined in `Secrets.h` (rename from `Secrets_Example.h`):

| Location | Leq bias | Lmax bias | Temp bias | Hum bias | RSSI bias | Rationale |
|---|---|---|---|---|---|---|
| Lunch Area | +12 dB | +8 dB | +1.5 °C | +3 % | 0 dBm | Busy tables; best Wi-Fi (closest to router) |
| Entrance | +8 dB | +14 dB | −1 °C | −5 % | −15 dBm | Door slams drive high Lmax; cooler, signal drops when glass door opens |
| Close to Kitchen | +22 dB | +10 dB | +8 °C | +18 % | −28 dBm | Loudest, hottest, most humid; steel appliances attenuate Wi-Fi |
| Close to Restrooms | +3 dB | +5 dB | −0.5 °C | +6 % | −12 dBm | Quietest zone; slightly humid |
| External Area | +15 dB | +3 dB | −0.5 °C | +6 % | −15 dBm | Outdoor street noise; mild weather; partial Wi-Fi coverage |

---

## OLED Display State Machine

The SSD1306 128×64 display cycles through **5 screens**, advanced by pressing the Display button (GPIO 26):

| State | Screen | Content |
|---|---|---|
| 0 | **Numeric: SPL** | Leq (large), Lmax, Lmin, Wi-Fi RSSI icon |
| 1 | **Numeric: Temp/Hum** | Temperature (°C), Humidity (%), Wi-Fi RSSI icon |
| 2 | **Numeric: Network** | IP address, RSSI bar, MQTT status |
| 3 | **Chart: SPL** | Sparkline of last 20 Leq readings (ring buffer), auto-ranged Y axis |
| 4 | **Chart: Temp+Hum** | Dual sparkline — temperature (solid) and humidity (dashed), auto-ranged |

### Chart Infrastructure

- **`autoRange(values, n, snapStep, minSpan, &outMin, &outMax)`** — snaps min/max to the nearest `snapStep` increment and enforces a minimum Y span (`minSpan`) to prevent degenerate flat-line charts.
- **`drawSparkline(values, n, yMin, yMax, x0, y0, w, h, dashed)`** — draws connected line segments through the ring buffer; optional dashed mode for the humidity overlay.
- **`drawWifiRSSI(rssi, x, y)`** — renders a 4-bar Wi-Fi icon using thresholds at −55, −67, and −80 dBm. An RSSI sentinel value of `1` indicates "not connected" and renders all bars hollow.

---

## MQTT Publishing

### Per-Device Credential Rotation

Each of the 5 virtual devices has its own Konker MQTT username, password, and publish topic (defined in `Secrets.h`). The publish cycle iterates over all devices sequentially:

1. Call `mqttReconnect(device)` — establishes a fresh MQTT connection using that device's credentials.  
   - Client ID format: `"ESP32_" + device.mqttUser`  
   - Up to **3 retries** with 2-second delays between attempts.
2. Call `client.publish(device.mqttTopic, payload)` to send the JSON payload.
3. Call `client.disconnect()` to release the connection before moving to the next device.

This credential-rotation approach allows one ESP32 to present as five independent Konker devices without maintaining simultaneous connections. The broker host and port are listed in the [Overview](#overview) table and configured in `Secrets.h`.

---

## SD Card Logging

`logSDCard.ino` logs the **physical sensor readings** (from the real microphone and DHT22) to `/data.txt` on the MicroSD card. Writes are buffered in a `std::vector<String>` (buffer size = `BUFFER_SIZE = 15` records) and flushed to disk in a single `appendFile()` call when the buffer reaches capacity.

### CSV Format

```
ESP DATA IoT
CUSTOMER: PUT_HERE_THE_CUSTOMER_NAME
SECTOR:   PUT_HERE_THE_SECTOR_NAME
LOCAL:    PUT_HERE_THE_LOCAL_NAME

timestamp;customer_ID;iot_device_serial_number;temperature;humidity;avg_spl;max_spl;wifi_status
```

The device serial number is set by `#define ESP32_DEVICE_ID "001Corp20250122"` in `logSDCard.ino`.

If the SD card is absent or fails to mount, the display shows an error message for 2 seconds and the firmware continues without logging.

---

## Wi-Fi & Captive Portal Setup

Wi-Fi credentials are provisioned using **WiFiManager**. On the first boot (or whenever the stored credentials fail), pressing the Wi-Fi button (GPIO 25) launches an access point:

| Item | Value |
|---|---|
| AP Name | `Ambi IoT Device` |
| AP IP | `192.168.4.1` |

Connect any phone or laptop to this network and a captive portal opens automatically. Enter your Wi-Fi SSID and password; the ESP32 saves the credentials and reboots.

NTP timezone is configured for **GMT−4 + 1 h DST** using `pool.ntp.org`, matching the `America/Sao_Paulo` timezone used by the Android app.

---

## RTC & NTP Time Sync

The DS3232 RTC provides accurate timestamps between power cycles. Time synchronisation flow:

1. After the first successful Wi-Fi connection, `getNTPClientDateTimeAndSetDS3231RTC()` fetches the current time from `pool.ntp.org` and writes it to the DS3232 via I²C.
2. A flag (`ntpSynced`) prevents repeated syncs — subsequent boots reuse the RTC time directly.
3. All timestamps use the format `yyyy.MM.ddTHH:mm:ss`, which the Konker IoT platform and the Ambi Android app both parse without conversion.

---

## JSON Payload Format

Each MQTT publish sends a single JSON object:

```json
{
  "reading_time":    "2025.01.22T14:30:00",
  "customer_ID":     "Customer_ID",
  "device_location": "Lunch Area",
  "temperature":     23.50,
  "humidity":        46.20,
  "leq_spl":         67.34,
  "lmax_spl":        75.12,
  "lmin_spl":        61.05,
  "wifi_rssi":       -45
}
```

| Field | Type | Unit | Source |
|---|---|---|---|
| `reading_time` | String | `yyyy.MM.ddTHH:mm:ss` | DS3232 RTC |
| `customer_ID` | String | — | `Secrets.h` |
| `device_location` | String | — | `IoTDevice.location` |
| `temperature` | Float (2 dp) | °C | DHT22 / SimProfile |
| `humidity` | Float (2 dp) | % | DHT22 / SimProfile |
| `leq_spl` | Float (2 dp) | dB | IEC 61672 Leq / SimProfile |
| `lmax_spl` | Float (2 dp) | dB | Running max / SimProfile |
| `lmin_spl` | Float (2 dp) | dB | Running min / SimProfile |
| `wifi_rssi` | Integer | dBm | `WiFi.RSSI()` / SimProfile |

---

## Getting Started

### Prerequisites

- Arduino IDE 2.x (or PlatformIO)
- ESP32 board package installed (`espressif/arduino-esp32 ≥ 2.0`)
- All libraries listed in [Library Dependencies](#library-dependencies) installed
- MicroSD card formatted as FAT32
- Active Konker account with 5 registered devices (one per simulated location)

### Setup Steps

1. **Clone / copy** the `main/` folder to your Arduino sketchbook.

2. **Rename** `Secrets_Example.h` to `Secrets.h`.

3. **Edit `Secrets.h`:**
   - Set `customer_ID` to your organisation name.
   - Replace the placeholder `mqttUser`, `mqttPass`, and `mqttTopic` values for each of the 5 devices with your actual Konker credentials.
   - Adjust `SimProfile` biases if your restaurant layout differs.

4. **Wire the hardware** according to the [Pin Map](#pin-map).

5. **Insert** a FAT32-formatted MicroSD card.

6. **Upload** the sketch to your ESP32.

7. **Provision Wi-Fi:** on first boot, press the Wi-Fi button (GPIO 25), connect to `Ambi IoT Device`, and enter your credentials in the captive portal.

8. **Verify:** watch the OLED display cycle through screens. After 15 minutes, check your Konker dashboards for incoming data from all 5 devices.

---

## Configuration Reference

All tuneable constants are in `main.ino` and `Secrets.h`:

| Constant | File | Default | Description |
|---|---|---|---|
| `PUBLISH_INTERVAL` | `main.ino` | `900000` ms | Time between publish cycles (15 min) |
| `CHART_POINTS` | `main.ino` | `20` | Ring-buffer depth for OLED sparklines |
| `BUFFER_SIZE` | `logSDCard.ino` | `15` | SD write-buffer depth (records) |
| `MQTT_SERVER` | `Secrets.h` | `ucmp.soneca.dev` | Konker MQTT broker hostname |
| `MQTT_PORT` | `Secrets.h` | `1883` | MQTT broker port |
| `customer_ID` | `Secrets.h` | `"Customer_ID"` | Label written to every payload |
| `ESP32_DEVICE_ID` | `logSDCard.ino` | `"001Corp20250122"` | SD log device serial number |
| `LEQ_FLOOR` | `processAudioSPL.ino` | `55.0 dB` | Simulation quiet-floor baseline |
| `RSSI_REF` | `processAudioSPL.ino` | `−45 dBm` | Simulation Wi-Fi reference level |
| `SPL_MIN_DB` | `processAudioSPL.ino` | `30 dB` | ADC lower bound mapping |
| `SPL_MAX_DB` | `processAudioSPL.ino` | `120 dB` | ADC upper bound mapping |

---

*Developed as the final project for the "IoT e Android Embarcado" course at Unicamp.*
