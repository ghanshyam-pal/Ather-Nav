package com.athernav.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;

/**
 * Monitors system stats: battery, network, phone temperature
 * Updates every 30 seconds
 */
public class SystemMonitor {

    public interface SystemCallback {
        void onSystemUpdate(String dashboardText);
    }

    private final Context context;
    private SystemCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 30 * 1000; // 30 seconds
    private int displayIndex = 0; // cycles through battery → network → temp

    public SystemMonitor(Context context) {
        this.context = context;
    }

    public void start(SystemCallback cb) {
        this.callback = cb;
        update();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                update();
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    public void stop() {
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    private void update() {
        // Cycle through 3 stats every update
        String text;
        switch (displayIndex % 3) {
            case 0: text = getBattery(); break;
            case 1: text = getNetwork(); break;
            case 2: text = getTemp(); break;
            default: text = getBattery();
        }
        displayIndex++;
        if (callback != null) callback.onSystemUpdate(text);
    }

    private String getBattery() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intent = context.registerReceiver(null, filter);
        if (intent == null) return "BAT-UNKNOWN";

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        int pct = (int) ((level / (float) scale) * 100);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL;

        String prefix = charging ? "CHG" : "BAT";
        return prefix + "-" + pct + "PC";
    }

    private String getNetwork() {
        ConnectivityManager cm = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "NET-UNKNOWN";

        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return "NO-NETWORK";

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "NET-WIFI";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            TelephonyManager tm = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
            return "NET-" + getNetworkType(tm);
        }
        return "NET-UNKNOWN";
    }

    private String getNetworkType(TelephonyManager tm) {
        if (tm == null) return "DATA";
        try {
            int type = tm.getDataNetworkType();
            switch (type) {
                case TelephonyManager.NETWORK_TYPE_LTE: return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:  return "5G";
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP: return "3G";
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS: return "2G";
                default: return "DATA";
            }
        } catch (Exception e) {
            return "DATA";
        }
    }

    private String getTemp() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intent = context.registerReceiver(null, filter);
        if (intent == null) return "TEMP-UNKNOWN";
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10;
        return "PHN-" + temp + "C";
    }
}
