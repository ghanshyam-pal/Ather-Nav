package com.athernav.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.media.session.MediaButtonReceiver;

/**
 * Required for Ather BLE to recognize us as a legitimate media app.
 * VLC has this registered — we were missing it entirely.
 * Ather's BLE checks mediaButtonReceiver field in the session dump —
 * if null, it ignores the session.
 */
public class AtherNavMediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Forward media button events to MediaButtonReceiver helper
        // which routes them to our MediaSessionService callback
        MediaButtonReceiver.handleIntent(
            MediaSessionService.getMediaSession(), intent);
    }
}
