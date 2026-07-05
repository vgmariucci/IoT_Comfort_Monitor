# Ambi — ComfortPlaces IoT Monitor

> **Final Project — "IoT e Android Embarcado" Course · Unicamp**

Ambi is an Android application that monitors acoustic and environmental comfort parameters in real time for a restaurant environment. It connects to the **Konker IoT platform**, fetches sensor readings via REST API, and presents them through animated, multi-lingual dashboards and an interactive floor plan.

---

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Architecture](#architecture)
4. [Tech Stack](#tech-stack)
5. [Project Structure](#project-structure)
6. [IoT Integration](#iot-integration)
7. [Screens](#screens)
8. [Getting Started](#getting-started)
9. [Configuration](#configuration)
10. [Sensor Payload](#sensor-payload)

---

## Overview

| Item | Detail |
|---|---|
| Platform | Android (API 26+) |
| Language | Kotlin |
| UI Toolkit | Jetpack Compose + Material 3 |
| IoT Backend | Konker Platform (`api-ucmp.soneca.dev`) |
| Auth | OAuth 2.0 — Client Credentials (Bearer token) |
| Auto-refresh | Every 30 seconds |
| Data window | Last 7 days |
| Permissions | `INTERNET` only |

---

## Features

### Multi-language Support
The app supports three languages, switchable at any time from both the login screen and the main app header:

- 🇺🇸 English
- 🇧🇷 Português BR
- 🇪🇸 Español

Language switching is instant and fully reactive — every label, gauge title, zone name, and heatmap header updates without restarting the app. The system is built on a `CompositionLocal`-driven `AppStrings` data class injected at the root level.

### Dashboard
The dashboard aggregates the last 7 days of sensor data into four cards:

**Noise Heatmap** — A 7-day × 3-period (Morning / Afternoon / Evening) matrix showing the average Leq per location. The grid is filterable by zone (All / Lunch / Entrance / Kitchen / Restrooms / Outdoor) and color-coded from green (quiet) through amber (moderate) to red (loud). Readings between 00:00–05:59 are excluded from the heatmap aggregation.

**Sound Level Chart** — Line chart of the last 10 Leq readings, powered by the Vico charting library.

**Temperature & Humidity Chart** — Dual-line chart of the last 10 temperature (°C) and humidity (%) readings.

**Wi-Fi RSSI Card** — Signal strength gauge with four quality bands: Excellent / Good / Fair / Poor.

### Floor Plan (Interactive Map)
A top-down, Sims-style pixel-art restaurant floor plan drawn entirely with Jetpack Compose `Canvas`. It visualises the five monitored zones:

| Key | Display name | Position on grid |
|---|---|---|
| Close to Kitchen | Kitchen | Top-left |
| Close to Restrooms | Restrooms | Top-right |
| Lunch Area | Dining | Centre |
| Entrance | Entrance | Lower centre |
| External Area | Outdoor Patio | Bottom |

Each zone features:
- **Comfort overlay** — semi-transparent tint (green / amber / red) based on live Leq
- **Selection highlight** — pulsing animated border; strobe mode (220 ms cycle) when Leq > 80 dB
- **Sensor dot** — coloured dot with a reading badge displaying the current dB value
- **Room label** — localised, drawn with `NativePaint` for crisp sub-pixel text

#### Comfort Inspector Character
A Pokémon Yellow-inspired pixel-art sprite titled **"Comfort Inspector"** lives on the map. When the user taps a zone button, the character walks smoothly from its current position to the target zone using parallel `Animatable` coroutines for X and Y axes (900 ms, linear easing) with a 4-frame walk cycle (120 ms per frame). On arrival, a thought bubble fades in showing the zone's dB reading, then fades out after 2.8 seconds.

### Authentication
Login screen connects to the Konker OAuth 2.0 token endpoint. Credentials are always verified against the server on login (no bypass). A valid token is cached in `SharedPreferences` with a 5-minute safety buffer before expiry; subsequent API calls reuse the cached token and only re-fetch when needed.

---

## Architecture

The app follows **MVVM** with **Unidirectional Data Flow** and dependency injection via **Hilt**.

```
┌──────────────────────────────────────────────────────┐
│                      UI Layer                        │
│  LoginScreen · DashboardScreen · FloorPlanScreen     │
│  (Jetpack Compose — stateless, driven by ViewModel)  │
└──────────────────┬───────────────────────────────────┘
                   │ observes state
┌──────────────────▼───────────────────────────────────┐
│                  ViewModel Layer                     │
│  LoginViewModel · DashboardViewModel                 │
│  (Hilt @HiltViewModel, viewModelScope coroutines)    │
└──────────────────┬───────────────────────────────────┘
                   │ calls suspend functions
┌──────────────────▼───────────────────────────────────┐
│               Repository Layer                       │
│  KonkerRepository                                    │
│  (orchestrates auth token + API calls)               │
└──────────┬────────────────────────┬──────────────────┘
           │                        │
┌──────────▼──────────┐  ┌─────────▼──────────────────┐
│    KonkerApi        │  │      TokenManager           │
│  (Retrofit/OkHttp)  │  │  (OAuth2 + SharedPrefs     │
│                     │  │   token cache)              │
└─────────────────────┘  └────────────────────────────┘
```

**Key design decisions:**

- `DashboardViewModel` receives credentials via `SavedStateHandle` (passed through the navigation back stack after successful login) — no global state or singleton credential store.
- `KonkerRepository` resolves the device GUID lazily and caches it in memory for the session lifetime, avoiding repeated device-list API calls.
- All network I/O runs on `Dispatchers.IO` inside `withContext` blocks.
- The floor plan composable (`CompactPixelMap`) is fully self-contained: it holds its own animation state (`Animatable`, `rememberInfiniteTransition`) and receives only read-only data from the ViewModel.

---

## Tech Stack

| Library | Version | Purpose |
|---|---|---|
| Android Gradle Plugin | 8.13.2 | Build toolchain |
| Kotlin | 2.1.0 | Language |
| KSP | 2.1.0-1.0.29 | Annotation processing |
| Jetpack Compose BOM | 2024.05.00 | UI components |
| Material 3 | — | Design system |
| Activity Compose | 1.9.0 | Entry point integration |
| Navigation Compose | 2.7.7 | In-app navigation |
| Lifecycle ViewModel Compose | 2.8.0 | ViewModel integration |
| Hilt | 2.51.1 | Dependency injection |
| Hilt Navigation Compose | 1.2.0 | `hiltViewModel()` in Compose |
| Retrofit | 2.11.0 | HTTP client (REST) |
| OkHttp | 4.12.0 | HTTP engine + logging |
| Gson Converter | 2.11.0 | JSON deserialization |
| Vico | 1.15.0 | Compose-native charts |
| Security Crypto | 1.1.0-alpha06 | Encrypted SharedPreferences |
| Kotlinx Coroutines Android | 1.8.1 | Async / concurrency |

---

## Project Structure

```
app/src/main/java/com/example/comfortplaces/
│
├── ComfortPlacesApp.kt          # Hilt application class
├── MainActivity.kt              # Single-activity host; nav graph + language state
│
├── data/
│   ├── model/
│   │   ├── SensorReading.kt     # Core data model (leqSpl, lmaxSpl, lminSpl,
│   │   │                        #   temperature, humidity, wifiRssi, deviceLocation)
│   │   ├── DeviceEvent.kt       # Raw Konker event; extension fun toSensorReading()
│   │   ├── DevicesResponse.kt   # Device list response wrapper
│   │   └── TokenResponse.kt     # OAuth token response
│   ├── remote/
│   │   ├── KonkerApi.kt         # Retrofit interface (getDevices, getOutgoingEvents)
│   │   ├── NetworkModule.kt     # Hilt @Module — provides Retrofit + OkHttp
│   │   └── TokenManager.kt      # OAuth2 token fetch, cache, and refresh logic
│   └── repository/
│       └── KonkerRepository.kt  # Aggregates auth + data-fetch; maps to SensorReading
│
└── ui/
    ├── theme/
    │   └── Theme.kt             # Material 3 dark theme
    ├── language/
    │   ├── AppLanguage.kt       # AppLanguage enum, AppStrings data class,
    │   │                        #   EN / PT / ES string objects
    │   └── LanguageSelector.kt  # Flag-button row composable
    ├── login/
    │   ├── LoginScreen.kt       # Login UI with language selector
    │   └── LoginViewModel.kt    # Hilt ViewModel for login flow
    ├── dashboard/
    │   ├── DashboardScreen.kt   # Root composable; wires four cards
    │   ├── DashboardViewModel.kt # Polls API every 30 s; groups by location
    │   ├── HeatmapCard.kt       # 7-day × 3-period noise heatmap
    │   ├── HeatmapData.kt       # DayPeriod enum + buildLocationHeatmaps()
    │   ├── SoundCard.kt         # Leq line chart (Vico)
    │   ├── TempHumCard.kt       # Temperature + humidity chart (Vico)
    │   └── RssiCard.kt          # Wi-Fi RSSI gauge
    └── floorplan/
        ├── CompactPixelMap.kt   # Canvas floor plan + Comfort Inspector sprite
        ├── FloorPlanScreen.kt   # Layout: map (42%) + gauges (58%) + zone buttons
        └── SensorAnimatedCard.kt # Individual animated gauge cards
```

---

## IoT Integration

### Konker Platform

The app connects to a private Konker IoT deployment:

- **Base URL:** `https://api-ucmp.soneca.dev`
- **Auth endpoint:** `POST /v1/oauth/token` (Basic auth → Bearer token)
- **Events endpoint:** `GET /v1/{application}/outgoingEvents`
- **Device name:** `comfort_places_app`

### Data Fetching Flow

1. On `DashboardViewModel` init, credentials from `SavedStateHandle` are used to call `KonkerRepository.getSensorReadings()`.
2. The repository calls `TokenManager.getValidToken()` — returns cached token if not expiring within 5 minutes, otherwise fetches a new one.
3. The device GUID for `comfort_places_app` is resolved via `GET /v1/default/devices/` and cached in memory.
4. Events are fetched with a query `device:{guid} timestamp:>{since}` for the last 7 days, sorted newest-first, up to 10,000 records.
5. Each raw event is mapped to `SensorReading` via `DeviceEvent.toSensorReading()`, which extracts `deviceLocation` from the payload key and parses all numeric fields.
6. The ViewModel groups readings by `deviceLocation` and exposes the map as observable Compose state.
7. Steps 1–6 repeat automatically every 30 seconds.

---

## Screens

### Login
- Username and password fields (Konker credentials)
- Language selector (flag chips) — EN / PT-BR / ES
- Validation: empty fields are rejected before the network call

### Dashboard (Tab 1)
- **Top bar:** "Ambi" title + language selector
- **Noise Heatmap:** filterable by zone; green→amber→red color scale; 45–90 dB range
- **Sound Chart:** last 10 Leq readings as a line chart
- **Temp & Humidity Chart:** last 10 readings, dual axis
- **RSSI Card:** signal quality with text indicator

### Floor Plan (Tab 2)
- Left column (42% width): interactive Canvas map + zone selector buttons
- Right column (58% width): four animated gauge cards for the selected zone
  - Sound Leq (dB)
  - Temperature (°C)
  - Humidity (%)
  - Wi-Fi RSSI (dBm)
- Peak card at bottom: Lmax / Lmin values
- Legend strip: colour key for dB ranges

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1) or later
- JDK 17
- Android device or emulator running API 26+
- Active Konker account with a registered device publishing the correct payload format

### Build & Run

```bash
# Clone the repository
git clone <repo-url>
cd ComfortPlacesApp_1

# Open in Android Studio and sync Gradle, or build from CLI:
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### Login

Launch the app and enter your **Konker username and password**. The app will authenticate against the server and navigate to the main screen on success.

---

## Configuration

All backend configuration is centralised in `TokenManager.kt` and `KonkerRepository.kt`:

| Constant | Location | Default |
|---|---|---|
| `BASE_URL` | `TokenManager` | `https://api-ucmp.soneca.dev` |
| `APP` | `KonkerRepository` | `default` |
| `DEVICE_NAME` | `KonkerRepository` | `comfort_places_app` |

To point the app at a different Konker instance or device, update these constants and rebuild.

---

## Sensor Payload

Each Konker event must include the following fields in its payload. The `deviceLocation` field maps to one of the five monitored zones:

| Field | Type | Unit | Description |
|---|---|---|---|
| `deviceLocation` | String | — | Zone key (`"Lunch Area"`, `"Close to Kitchen"`, `"Close to Restrooms"`, `"Entrance"`, `"External Area"`) |
| `leqSpl` | Float | dB | Equivalent continuous sound level (IEC 61672) |
| `lmaxSpl` | Float | dB | Maximum sound level during the measurement period |
| `lminSpl` | Float | dB | Minimum sound level during the measurement period |
| `temperature` | Float | °C | Ambient temperature |
| `humidity` | Float | % | Relative humidity |
| `wifiRssi` | Int | dBm | Wi-Fi received signal strength |

Timestamps must use the Konker format: `yyyy.MM.dd'T'HH:mm:ss` in the `America/Sao_Paulo` timezone.

---

## Noise Thresholds

The app uses the following Leq thresholds for colour-coding across all views:

| Range | Colour | Meaning |
|---|---|---|
| No data / 0 dB | Grey | No reading |
| < 60 dB | Green | Comfortable |
| 60 – 75 dB | Amber | Moderate |
| > 75 dB | Red | Uncomfortable |
| > 80 dB | Red (strobe) | Critical — floor plan border pulses rapidly |

---

*Developed as the final project for the "IoT e Android Embarcado" course at Unicamp.*
