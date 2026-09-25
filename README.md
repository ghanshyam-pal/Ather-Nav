# AtherNav - Google Maps → Bluetooth Dashboard Navigation

Mirror Google Maps turn-by-turn navigation directly to your Ather Rizta S (and compatible Bluetooth/AVRCP vehicle dashboards, car stereos, or smart displays) via Bluetooth MediaSession.

Also features live Weather, Cricket score tracker, Notification/OTP preview, and Phone System monitor modes!

---

## 📲 Quick Install (Universal APK)

A prebuilt, signed, universal release APK is included in this repository at:
👉 **[`output/app.apk`](output/app.apk)** (4.5 MB)

### Option 1: Direct Phone Download / Sideload
1. Transfer or download **`output/app.apk`** to your Android phone (via WhatsApp, Google Drive, Telegram, or Chrome).
2. Tap the APK file to install it.
3. Allow "Install unknown apps" if prompted by your browser or file manager.

### Option 2: Install via ADB (USB / Wireless)
```bash
adb install -r output/app.apk
```

---

## ⚙️ First-Time Phone Setup (Works on All Android Devices)

Open the **AtherNav** app and complete the 3 setup steps shown on the main screen:

### 1. Notification Listener Access
- Tap **GRANT NOTIFICATION ACCESS**.
- Select **AtherNav** and enable the toggle.
> [!IMPORTANT]
> **Android 13 / 14 / 15 "Restricted setting" Fix:**
> If the toggle is greyed out with the message: *"Restricted setting: For your security, this setting is currently unavailable"*:
> 1. In AtherNav, tap **ALLOW RESTRICTED SETTINGS (ANDROID 13+)** (or open Phone Settings → Apps → AtherNav).
> 2. Tap the **three dots (⋮)** in the top-right corner.
> 3. Tap **Allow restricted settings** and confirm with your fingerprint/PIN.
> 4. Return to AtherNav and tap **GRANT NOTIFICATION ACCESS** again.

### 2. Phone Permissions
- Tap **GRANT PERMISSIONS** to allow:
  - **Notifications (`POST_NOTIFICATIONS`)**: Keeps the background foreground service alive.
  - **Nearby Devices / Bluetooth (`BLUETOOTH_CONNECT`)**: Required on Android 12+ for Bluetooth reconnect detection.

### 3. Battery Optimization (Crucial for background stability)
- Tap **SET BATTERY TO UNRESTRICTED** and select "Allow".
- *For Xiaomi (MIUI/HyperOS), Samsung (OneUI), OnePlus (OxygenOS), Oppo, and Vivo:*
  - Open App Info → Battery → Select **Unrestricted** / **No restrictions**.
  - Enable **Autostart** in your phone's app settings.

---

## 🚀 How to Use

1. Turn on your scooter / vehicle and ensure your phone is connected via Bluetooth.
2. Open **AtherNav** and tap **START SERVICE**.
3. Open **Google Maps** and start navigation.
4. Watch turn-by-turn directions live on your dashboard!

### Cycling Modes
- Tap **SWITCH MODE** in the app or use your handlebar Next/Previous buttons to cycle through:
  - `AUTO` (**Smart HUD - Default**): Adaptive cockpit! Locks onto turn navigation when turn is imminent (< 300m), and intelligently alternates between Turn, ETA, Weather, Battery, and Trip stats while cruising straight.
  - `NAV`: Formatted turn directions (`200M TURN-LEFT`, `RNBT-EXIT-2`)
  - `ETA`: Live arrival time, remaining distance, and remaining duration (`ETA 18-45`, `REM 12-4KM`, `REM 25MIN`)
  - `TRIP`: GPS Trip Computer (`TRIP 14-2KM`, `TIME 28MIN`, `MAX 62KM`, `AVG 38KM`)
  - `RAW`: Full Google Maps text scrolling
  - `WEATHER`: Live temperature and weather condition (e.g. `32C-CLEAR`, `28C-RAIN`)
  - `NOTIFY`: WhatsApp sender preview and SMS OTP alerts (`OTP-482910`, `RAHUL-MEETING`)
  - `SYSTEM`: Phone battery percentage, network speed, and device temperature (`BAT-85PC`, `NET-5G`, `PHN-34C`)
  - `SPORTS`: Live cricket match scores (`IND-185-3`)
  - `MUSIC`: Active media playback track title (Spotify / YT Music)

### 🚨 Real-Time Priority Alerts (Override Any Screen)
- **Traffic Delays & Accidents:** Flashes `JAM +12MIN`, `SLOW-TRAFFIC`, or `ACCIDENT-AHD` when detected.
- **Speed Cameras:** Flashes `CAM 500M` or `CAMERA-AHD` when approaching speed cameras.
- **Over-Speeding Warning:** Flashes `SLOW-DOWN` when scooter speed exceeds 60 km/h via phone GPS.
- **Roundabout Exit Numbers:** Accurately displays exit number (`RNBT-EXIT-1` through `RNBT-EXIT-6`).

---

## 🛠️ Building from Source

### Requirements
- JDK 17+
- Android SDK (API 34 platform & build-tools)

### Build Commands
To build the signed universal release APK with a single command:
```bash
./build_apk.sh
```
Or with Gradle:
```bash
./gradlew assembleRelease
```
The compiled, optimized APK will be produced at `output/app.apk` and `app/build/outputs/apk/release/app-release.apk`.

### Automated GitHub CI/CD
A GitHub Actions workflow is included at [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml). Every push or release tag automatically builds and publishes the APK as a downloadable artifact.

---

## 📱 Compatibility Matrix

| Feature | Supported Range | Notes |
|---|---|---|
| **Android Versions** | Android 8.0 (API 26) through Android 15+ | `minSdk 26`, `targetSdk 34` |
| **Brands / ROMs** | Samsung OneUI, Google Pixel, Xiaomi MIUI/HyperOS, OnePlus OxygenOS, Nothing OS, Motorola, Oppo ColorOS, Vivo FuntouchOS | Handled runtime permissions, foreground service types, and Doze exemptions |
| **Dashboards / Vehicles** | Ather Rizta S, Ather 450X, Ola S1, TVS iQube, Royal Enfield Tripper, Car Bluetooth AVRCP head units, Bluetooth helmets (Sena, Cardo) | Uses standard Bluetooth MediaBrowser & MediaSession AVRCP |

---

## 📄 License
This project is licensed under the [MIT License](LICENSE).
