package com.athernav.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Tracks trip metrics via GPS:
 * - Trip Distance (TRIP 14-2KM)
 * - Duration (TIME 28MIN)
 * - Top Speed (MAX 64KM)
 * - Average Speed (AVG 38KM)
 * - Speed limit alerts (SLOW-DOWN when speed > 60 km/h)
 */
public class TripComputer {

    public interface TripCallback {
        void onTripStatsUpdate(String dashboardText);
        void onSpeedLimitAlert(String alertText);
    }

    private final Context context;
    private TripCallback callback;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private float currentSpeedKmh = 0f;
    private float maxSpeedKmh = 0f;
    private float totalDistanceMeters = 0f;
    private long tripStartTimeMs = 0;
    private Location lastLocation = null;

    private static final float SPEED_LIMIT_KMH = 60.0f; // Alert threshold
    private long lastSpeedAlertTime = 0;

    private int displayToggle = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 3000; // 3 seconds per stat in TRIP mode

    public TripComputer(Context context) {
        this.context = context;
        this.tripStartTimeMs = System.currentTimeMillis();
    }

    public void start(TripCallback cb) {
        this.callback = cb;
        startLocationUpdates();
        startDisplayLoop();
    }

    public void stop() {
        stopLocationUpdates();
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;

            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    processLocation(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override
                public void onProviderEnabled(String provider) {}
                @Override
                public void onProviderDisabled(String provider) {}
            };

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 2.0f, locationListener, Looper.getMainLooper());
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 1000L, 2.0f, locationListener, Looper.getMainLooper());
            }
        } catch (Exception ignored) {}
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {}
        }
    }

    private void processLocation(Location location) {
        if (location == null) return;

        // Speed in km/h
        if (location.hasSpeed()) {
            currentSpeedKmh = location.getSpeed() * 3.6f;
        } else if (lastLocation != null && location.getTime() > lastLocation.getTime()) {
            float dist = location.distanceTo(lastLocation);
            float timeSec = (location.getTime() - lastLocation.getTime()) / 1000f;
            currentSpeedKmh = (dist / timeSec) * 3.6f;
        } else {
            currentSpeedKmh = 0f;
        }

        if (currentSpeedKmh > maxSpeedKmh) {
            maxSpeedKmh = currentSpeedKmh;
        }

        // Distance accumulator (filter out GPS drift when stationary)
        if (lastLocation != null && location.getAccuracy() < 30.0f) {
            float d = location.distanceTo(lastLocation);
            if (d >= 2.0f && d <= 120.0f) { // valid movement
                totalDistanceMeters += d;
            }
        }
        lastLocation = location;

        // Speed limit check (> 60 km/h)
        if (currentSpeedKmh >= SPEED_LIMIT_KMH) {
            long now = System.currentTimeMillis();
            if (now - lastSpeedAlertTime > 15000) { // Alert at most once every 15s
                lastSpeedAlertTime = now;
                if (callback != null) {
                    callback.onSpeedLimitAlert("SLOW-DOWN");
                }
            }
        }
    }

    private void startDisplayLoop() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onTripStatsUpdate(getCurrentStatText());
                }
                displayToggle++;
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    public String getCurrentStatText() {
        switch (displayToggle % 4) {
            case 0: return getDistanceText();
            case 1: return getDurationText();
            case 2: return getMaxSpeedText();
            case 3: return getAvgSpeedText();
            default: return getDistanceText();
        }
    }

    public String getDistanceText() {
        float km = totalDistanceMeters / 1000f;
        String val = String.format(Locale.US, "%.1f", km).replace(".", "-");
        return "TRIP " + val + "KM";
    }

    public String getDurationText() {
        long minutes = (System.currentTimeMillis() - tripStartTimeMs) / (60 * 1000);
        return "TIME " + minutes + "MIN";
    }

    public String getMaxSpeedText() {
        return "MAX " + Math.round(maxSpeedKmh) + "KM";
    }

    public String getAvgSpeedText() {
        float km = totalDistanceMeters / 1000f;
        float hours = (System.currentTimeMillis() - tripStartTimeMs) / (1000f * 3600f);
        int avg = hours > 0.01f ? Math.round(km / hours) : Math.round(currentSpeedKmh);
        return "AVG " + avg + "KM";
    }

    public float getCurrentSpeedKmh() {
        return currentSpeedKmh;
    }

    public void reset() {
        tripStartTimeMs = System.currentTimeMillis();
        totalDistanceMeters = 0;
        maxSpeedKmh = 0;
        lastLocation = null;
    }
}
