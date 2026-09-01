package com.athernav.app;

/**
 * Manages current mode and cycling between modes
 */
public class ModeManager {

    public enum Mode {
        NAV,        // Custom turn by turn: 200M TURN-LEFT
        RAW,        // Full Google Maps text scrolling
        WEATHER,    // Temp + rain alerts
        NOTIFY,     // WhatsApp + SMS OTP
        SYSTEM,     // Battery + network + temp
        SPORTS,     // Live cricket score
        MUSIC       // Spotify/any player control
    }

    private static Mode currentMode = Mode.NAV;

    private static final Mode[] ORDER = {
        Mode.NAV,
        Mode.RAW,
        Mode.WEATHER,
        Mode.NOTIFY,
        Mode.SYSTEM,
        Mode.SPORTS,
        Mode.MUSIC
    };

    public static Mode getCurrentMode() {
        return currentMode;
    }

    public static Mode nextMode() {
        int idx = indexOf(currentMode);
        currentMode = ORDER[(idx + 1) % ORDER.length];
        return currentMode;
    }

    public static Mode prevMode() {
        int idx = indexOf(currentMode);
        currentMode = ORDER[(idx - 1 + ORDER.length) % ORDER.length];
        return currentMode;
    }

    public static String getModeName() {
        switch (currentMode) {
            case NAV:     return "NAV-MODE";
            case RAW:     return "RAW-MODE";
            case WEATHER: return "WTHR-MODE";
            case NOTIFY:  return "NOTIF-MODE";
            case SYSTEM:  return "SYS-MODE";
            case SPORTS:  return "SPRT-MODE";
            case MUSIC:   return "MUSIC-MODE";
            default:      return "UNKNOWN";
        }
    }

    private static int indexOf(Mode mode) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == mode) return i;
        }
        return 0;
    }
}
