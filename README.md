> 🌐 **Language / Dil:** **🇬🇧 English** | [🇹🇷 Türkçe](README_TR.md)

# Real-Time Heart Rate Monitor

Bluetooth Low Energy (BLE) heart rate monitor: Android app + PHP server. Real-time data collection from Polar and other BLE-compatible heart rate sensors, server synchronization, and live monitoring via web dashboard.

| Component | Technology | Location |
|-----------|------------|----------|
| **Android App** | Kotlin, Jetpack Compose | [`app/`](app/) |
| **Web Dashboard** | PHP, Chart.js, SSE | [`server/`](server/) |

## Screenshots

### Live Heart Rate Screen
The main screen displays live heart rate data from the BLE sensor. Includes connection status, battery level, sensor contact, and signal quality indicators.

<p align="center">
  <img src="screenshots/01_live_heart_rate.jpg" width="300" alt="Live Heart Rate"/>
</p>

### Charts and Daily Statistics
Monitor heart rate graphs and RR intervals (ECG-like) in real-time. Daily minimum, average, and maximum values are displayed.

<p align="center">
  <img src="screenshots/02_graphs_daily_stats.jpg" width="300" alt="Charts and Statistics"/>
</p>

### Hourly Charts
Analyze heart rate data throughout the day with hourly charts.

<p align="center">
  <img src="screenshots/03_hourly_charts.jpg" width="300" alt="Hourly Charts"/>
</p>

### Hourly Summary Table
Detailed table showing minimum, average, maximum values and record count for each hour.

<p align="center">
  <img src="screenshots/04_hourly_summary.jpg" width="300" alt="Hourly Summary"/>
</p>

### All Records
List and review all heart rate records sent to the server.

<p align="center">
  <img src="screenshots/05_all_records.jpg" width="300" alt="All Records"/>
</p>

### Device Mode Settings
**Server Mode:** Collects data from BLE sensor and sends to server.
**Client Mode:** Monitors live data via WebView (while another device runs in Server mode).

You can also configure heart rate threshold values from this screen.

<p align="center">
  <img src="screenshots/06_device_mode_settings.jpg" width="300" alt="Device Mode"/>
</p>

### Alert and Service Settings
Configure vibration and sound alerts for threshold violations. Background service auto-start option.

<p align="center">
  <img src="screenshots/07_alert_settings.jpg" width="300" alt="Alert Settings"/>
</p>

### API Settings
Configure server URL and API key. The app can work with your own server.

<p align="center">
  <img src="screenshots/08_api_settings.jpg" width="300" alt="API Settings"/>
</p>

---

## Features

### BLE Connection
- Wahoo TICKR Fit and BLE Heart Rate Profile compatible devices
- Easily integrates with other sensors (Polar, Garmin, etc.) using standard BLE HR Profile
- Automatic reconnection
- Battery level monitoring
- Sensor contact detection
- RR intervals (beat-to-beat) reading

### Data Analysis
- Real-time heart rate chart
- RR intervals chart (ECG-like)
- Daily min/average/max statistics
- Detailed hourly analysis
- Signal quality calculation (HRV-based)

### Server Synchronization
- Data transmission via REST API
- Offline mode (local storage when no connection)
- Configurable API URL and key
- Live monitoring via WebView (Client mode)

### Alerts
- Threshold violation notifications (low/high heart rate)
- Vibration and sound alerts
- Customizable threshold values

### Background Service
- Continuous operation with Foreground Service
- Battery optimization management
- Manufacturer-specific battery settings guide
- Auto-start option

### Multi-Language Support
- Turkish and English interface
- Automatic system language detection
- Manual language switching from Settings
- Easily extensible structure (new languages can be added)

---

## Requirements

### Android App
- Android 8.0 (API 26) and above
- BLE-capable device
- Bluetooth, Location, Notification permissions

### Server
- PHP 8.1+
- MySQL 8.0+ / MariaDB 10.6+
- Apache or Nginx

---

## Installation

### Phase 1: Server (PHP Backend)

#### 1.1 Clone the repository

```bash
git clone https://github.com/burakyalti/realtime-heart-rate-monitor.git
cd realtime-heart-rate-monitor
```

#### 1.2 Copy server files to your web server

```bash
cp -r server/ /var/www/html/hr/
```

#### 1.3 Create the database

```bash
mysql -u root -p < server/schema.sql
```

This command automatically creates the `heart_rate_db` database and 3 tables:

| Table | Description |
|-------|-------------|
| `heart_rate_logs` | Raw heart rate data (BPM, RR intervals, battery, sensor contact) |
| `heart_rate_alerts` | Threshold violation alarms (low/high/no signal) |
| `heart_rate_stats` | Hourly statistics (min, max, average) |

#### 1.4 Set up environment variables

```bash
cd /var/www/html/hr/
cp .env.example .env
chmod 600 .env
nano .env
```

Fill in the `.env` file with your information:

```env
HR_DB_HOST=localhost
HR_DB_NAME=heart_rate_db
HR_DB_USER=your_db_user
HR_DB_PASS=your_db_password
HR_API_KEY=your-secret-api-key
HR_ALLOWED_DEVICES=XX:XX:XX:XX:XX:XX
```

| Variable | Description |
|----------|-------------|
| `HR_DB_HOST` | MySQL server address |
| `HR_DB_NAME` | Database name |
| `HR_DB_USER` | Database user |
| `HR_DB_PASS` | Database password |
| `HR_API_KEY` | Security key the Android app uses when sending data |
| `HR_ALLOWED_DEVICES` | Allowed BLE device MAC addresses (multiple: comma-separated) |

#### 1.5 Verify installation

Open in browser: `https://your-server.com/hr/live.php`

If the dashboard loads, server setup is complete. You'll see a "No data" message since there's no data yet.

### Phase 2: Android App

#### 2.1 Install the App

**Install from APK:**

1. Download the [hr.apk](hr.apk) file to your Android device
2. Allow "Install from unknown sources"
3. Complete the installation

**Or build from source:**

```bash
cd realtime-heart-rate-monitor
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

#### 2.2 App Settings

1. Open the app, go to **Settings** (gear icon in top right)
2. In **Server Settings** section:
   - **API URL:** `https://your-server.com/hr/api/log.php`
   - **API Key:** The `HR_API_KEY` value you set in `.env` file in Phase 1
3. Select **Server** mode in **Device Mode** section
4. Return to main screen and connect your BLE sensor

Once connected, data will automatically be sent to the server. You can monitor live at `live.php`.

---

## API Endpoints

### Send Data (Android App → Server)

```
POST /api/log.php
Headers:
  Content-Type: application/json
  X-API-Key: {API_KEY}

Body:
{
  "device_mac": "XX:XX:XX:XX:XX:XX",
  "heart_rate": 72,
  "rr_intervals": [850, 862, 845],
  "battery_level": 85,
  "sensor_contact": true,
  "timestamp": 1699876543210
}
```

### Data Reading Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/latest.php` | GET | Latest heart rate data (BPM, battery, time) |
| `/api/chart-data.php?seconds=60` | GET | Chart data (last N seconds) |
| `/api/minute-summary.php?hours=1` | GET | Per-minute average/min/max (last N hours) |
| `/api/sse.php` | GET | Server-Sent Events (real-time data stream) |
| `/api/history.php` | GET | Hourly statistics history |
| `/api/alerts.php` | GET | Alert history |

### Live Dashboard

```
GET /live.php
```

Live dashboard accessible from web browser or Android Client mode:

- Real-time BPM indicator (instant updates via SSE)
- Heart rate and ECG chart (30sec - 60min range)
- Range slider for time interval selection
- Per-minute average chart (1h - 24h)
- Battery level and signal status

---

## Technical Details

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **Architecture:** Single Activity + Compose Navigation
- **BLE:** Android Bluetooth LE API
- **HTTP Client:** Ktor

---

## Project Structure

### Android App
```
app/src/main/java/net/hrapp/hr/
├── MainActivity.kt           # Main activity and state management
├── ble/
│   └── BleManager.kt         # BLE connection management
├── data/
│   └── PreferencesManager.kt # SharedPreferences management
├── network/
│   └── ApiClient.kt          # HTTP requests
├── service/
│   ├── HeartMonitorService.kt    # Server mode foreground service
│   └── ClientMonitorService.kt   # Client mode foreground service
└── ui/
    ├── components/           # Reusable UI components
    ├── screens/              # Screen composables
    └── theme/                # Color and theme definitions
```

### PHP Server
```
server/
├── config.php                # Configuration (.env reading, DB connection)
├── live.php                  # Live dashboard (SSE, Chart.js, ECG)
├── schema.sql                # Database schema (3 tables)
├── .env.example              # Environment variables template
├── .gitignore
└── api/
    ├── log.php               # Data logging (POST - Android app sends)
    ├── latest.php            # Latest heart rate data (GET)
    ├── chart-data.php        # Chart data (GET, ?seconds=N)
    ├── minute-summary.php    # Per-minute average (GET, ?hours=N)
    ├── sse.php               # Server-Sent Events (real-time stream)
    ├── history.php           # Hourly statistics (GET)
    └── alerts.php            # Alert history (GET)
```

---

## License

This project is licensed under the MIT License.

---

## Contact

For questions or feedback, please use GitHub Issues.

---

## Disclaimer

> **This application is developed for personal use only and is not a medical device.**
>
> - The ECG chart in the application is a **representative visualization** derived from heart rate (BPM) data. It does not perform actual electrocardiogram (ECG/EKG) measurements.
> - The displayed data should **not be used as a reference** for diagnosis, treatment, or any health-related decisions.
> - Always consult a healthcare professional for health concerns.
>
> The developers accept no responsibility for any direct or indirect consequences arising from the use of this application.
