package com.athernav.app;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Listens to notifications from:
 * - Google Maps (navigation)
 * - WhatsApp (messages)
 * - SMS (OTP extraction)
 * - Any music app (now playing)
 */
public class NavNotificationListener extends NotificationListenerService {

    private static final String MAPS_PACKAGE    = "com.google.android.apps.maps";
    private static final String WHATSAPP_PKG    = "com.whatsapp";
    private static final String WHATSAPP_B_PKG  = "com.whatsapp.w4b"; // WhatsApp Business
    private static final String SMS_PKG         = "com.google.android.apps.messaging";
    private static final String SMS_PKG2        = "com.samsung.android.messaging";
    private static final String SPOTIFY_PKG     = "com.spotify.music";
    private static final String YT_MUSIC_PKG    = "com.google.android.apps.youtube.music";

    private String lastNavInstruction = "";
    private String lastNotifyText = "";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        CharSequence titleCs   = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs    = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextCs = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title    = titleCs   != null ? titleCs.toString()  : "";
        String text     = textCs    != null ? textCs.toString()   : "";
        String bigText  = bigTextCs != null ? bigTextCs.toString(): "";
        String fullText = bigText.isEmpty() ? text : bigText;

        // --- GOOGLE MAPS ---
        if (MAPS_PACKAGE.equals(pkg)) {
            handleMapsNotification(title, fullText);
            return;
        }

        // --- WHATSAPP ---
        if (WHATSAPP_PKG.equals(pkg) || WHATSAPP_B_PKG.equals(pkg)) {
            handleWhatsApp(title, fullText);
            return;
        }

        // --- SMS ---
        if (SMS_PKG.equals(pkg) || SMS_PKG2.equals(pkg)) {
            handleSMS(title, fullText);
            return;
        }

        // --- MUSIC APPS (Spotify, YT Music, etc) ---
        if (isMusicApp(pkg)) {
            handleMusicNotification(title, fullText);
            return;
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (MAPS_PACKAGE.equals(sbn.getPackageName())) {
            lastNavInstruction = "";
            sendToService("NAV-STOPPED", "NAV");
        }
    }

    // -------------------------
    // MAPS
    // -------------------------
    private void handleMapsNotification(String title, String text) {
        ModeManager.Mode mode = ModeManager.getCurrentMode();

        if (mode == ModeManager.Mode.RAW) {
            String raw = NavParser.sanitize(title.isEmpty() ? text : title);
            if (!raw.equals(lastNavInstruction)) {
                lastNavInstruction = raw;
                sendToService(raw, "NAV");
            }
        } else if (mode == ModeManager.Mode.NAV) {
            String parsed = NavParser.parse(title, text);
            if (!parsed.equals(lastNavInstruction)) {
                lastNavInstruction = parsed;
                sendToService(parsed, "NAV");
            }
        }
        // Other modes: ignore Maps notification while not in NAV/RAW
    }

    // -------------------------
    // WHATSAPP
    // -------------------------
    private void handleWhatsApp(String title, String text) {
        if (ModeManager.getCurrentMode() != ModeManager.Mode.NOTIFY) return;

        String sender  = NavParser.sanitize(title.length() > 6 ? title.substring(0, 6) : title);
        String msg     = NavParser.sanitize(text.length() > 20 ? text.substring(0, 20) : text);
        String display = sender + "-" + msg;

        if (!display.equals(lastNotifyText)) {
            lastNotifyText = display;
            sendToService(display, "NOTIFY");
        }
    }

    // -------------------------
    // SMS / OTP
    // -------------------------
    private void handleSMS(String title, String text) {
        if (ModeManager.getCurrentMode() != ModeManager.Mode.NOTIFY) return;

        String otp = extractOTP(text);
        String display;
        if (otp != null) {
            display = "OTP-" + otp;
        } else {
            String sender = NavParser.sanitize(title.length() > 5 ? title.substring(0, 5) : title);
            String msg    = NavParser.sanitize(text.length() > 15 ? text.substring(0, 15) : text);
            display = sender + "-" + msg;
        }

        if (!display.equals(lastNotifyText)) {
            lastNotifyText = display;
            sendToService(display, "NOTIFY");
        }
    }

    private String extractOTP(String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b(\\d{4,8})\\b");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) return m.group(1);
        return null;
    }

    // -------------------------
    // MUSIC APPS
    // -------------------------
    private boolean isMusicApp(String pkg) {
        return SPOTIFY_PKG.equals(pkg) || YT_MUSIC_PKG.equals(pkg)
            || "com.google.android.music".equals(pkg)
            || "com.amazon.mp3".equals(pkg)
            || "com.gaana".equals(pkg)
            || "com.jio.media.jiobeats".equals(pkg);
    }

    private void handleMusicNotification(String title, String text) {
        if (ModeManager.getCurrentMode() != ModeManager.Mode.MUSIC) return;

        String song   = NavParser.sanitize(title);
        String artist = NavParser.sanitize(text.length() > 10 ? text.substring(0, 10) : text);

        String display = song.isEmpty() ? "NO-TITLE" : song;
        if (!artist.isEmpty()) {
            display = song + " - " + artist;
        }

        sendToService(display, "MUSIC");
    }

    // -------------------------
    // SEND TO SERVICE
    // -------------------------
    private void sendToService(String text, String source) {
        Intent intent = new Intent(this, MediaSessionService.class);
        intent.setAction(MediaSessionService.ACTION_UPDATE);
        intent.putExtra(MediaSessionService.EXTRA_NAV_TEXT, text);
        intent.putExtra(MediaSessionService.EXTRA_SOURCE, source);
        startService(intent);
    }
}
