# AtherNav - Google Maps → Ather Rizta S Dashboard

Mirror Google Maps turn-by-turn navigation to your Ather Rizta S dashboard via MediaSession Bluetooth.

## How It Works

1. App listens to Google Maps notifications using NotificationListenerService
2. Parses navigation instruction (turn direction + distance)
3. Formats to 11-char uppercase display string (e.g. `200M TURN-LEFT`)
4. Pushes as MediaSession track title
5. Ather BLE reads MediaSession and shows it on dashboard

## Build Instructions

### Requirements
- Android Studio Hedgehog or newer
- JDK 8+
- Android phone (API 26+)

### Steps
1. Open Android Studio
2. File → Open → select this `AtherNav` folder
3. Wait for Gradle sync to complete
4. Connect your phone via USB
5. Enable USB Debugging on phone (Developer Options)
6. Click Run (green play button)

## First Time Setup on Phone

1. Open AtherNav app
2. Tap **GRANT NOTIFICATION ACCESS**
3. Find "AtherNav" in the list and enable it
4. Go back to app
5. Connect phone to Ather via Bluetooth
6. Tap **START SERVICE**
7. Open Google Maps → start navigation
8. Watch dashboard!

## Battery Optimization (IMPORTANT)

To prevent Android from killing the service:
- Settings → Apps → AtherNav → Battery → **Unrestricted**

## Display Format Examples

| Google Maps says | Dashboard shows |
|---|---|
| Turn left in 200 m | `200M TURN-LEFT` |
| Turn right in 500 m | `500M TURN-RIGHT` |
| Continue straight for 1.2 km | `1-2KM GO-STRAIGHT` |
| Keep left | `KEEP-LEFT` |
| Take the ramp on the right | `RAMP-RIGHT` |
| At the roundabout, take exit | `RNBT-R` |
| U-turn | `U-TURN-R` |
| You have arrived | `ARRIVED` |
| Rerouting | `REROUTING` |

## Files

```
app/src/main/java/com/athernav/app/
├── MainActivity.java           - UI, permission handling
├── NavNotificationListener.java - Reads Google Maps notifications  
├── MediaSessionService.java    - Maintains MediaSession for Ather BLE
└── NavParser.java              - Parses nav text to display format
```

## Troubleshooting

**Text not showing on dashboard:**
- Make sure Ather is connected via Bluetooth
- Check music is NOT playing (our session needs to be active)
- Restart MediaSessionService from app

**Wrong instruction showing:**
- Google Maps may use different wording - check logcat for raw notification text
- Report the raw text to improve NavParser keyword matching

**Service getting killed:**
- Set battery optimization to Unrestricted for AtherNav
