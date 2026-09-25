package com.athernav.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 1002;

    private TextView tvStatus;
    private TextView tvDashboard;
    private TextView tvPermission;
    private Button btnPermission;
    private Button btnRestrictedSettings;

    private TextView tvRuntimePerms;
    private Button btnRuntimePerms;

    private TextView tvBatteryStatus;
    private Button btnBatteryOptimization;

    private Button btnStartService;
    private Button btnStopService;
    private Button btnSwitchMode;

    private final BroadcastReceiver navReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("text");
            String mode = intent.getStringExtra("mode");
            if (text != null) {
                tvDashboard.setText(text);
                tvStatus.setText("MODE: " + (mode != null ? mode : "-") + " | ACTIVE");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvDashboard = findViewById(R.id.tvDashboard);

        // Step 1: Notification listener
        tvPermission = findViewById(R.id.tvPermission);
        btnPermission = findViewById(R.id.btnPermission);
        btnRestrictedSettings = findViewById(R.id.btnRestrictedSettings);

        // Step 2: Runtime permissions
        tvRuntimePerms = findViewById(R.id.tvRuntimePerms);
        btnRuntimePerms = findViewById(R.id.btnRuntimePerms);

        // Step 3: Battery optimization
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization);

        // Service controls
        btnStartService = findViewById(R.id.btnStartService);
        btnStopService = findViewById(R.id.btnStopService);
        btnSwitchMode = findViewById(R.id.btnSwitchMode);

        btnPermission.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Notification Settings", Toast.LENGTH_SHORT).show();
            }
        });

        btnRestrictedSettings.setOnClickListener(v -> {
            openAppDetailsSettings();
        });

        btnRuntimePerms.setOnClickListener(v -> {
            requestRequiredPermissions();
        });

        btnBatteryOptimization.setOnClickListener(v -> {
            requestIgnoreBatteryOptimization();
        });

        btnSwitchMode.setOnClickListener(v -> {
            Intent intent = new Intent(this, MediaSessionService.class);
            intent.setAction(MediaSessionService.ACTION_SWITCH_MODE);
            startService(intent);
        });

        btnStartService.setOnClickListener(v -> {
            Intent intent = new Intent(this, MediaSessionService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            updateUI();
        });

        btnStopService.setOnClickListener(v -> {
            stopService(new Intent(this, MediaSessionService.class));
            tvDashboard.setText("---");
            updateUI();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register broadcast receiver for nav updates
        IntentFilter filter = new IntentFilter("com.athernav.app.NAV_UPDATE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(navReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(navReceiver, filter);
        }

        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(navReceiver);
        } catch (Exception ignored) {}
    }

    private void updateUI() {
        boolean hasNotificationAccess = isNotificationListenerEnabled();
        boolean hasRuntimePerms = checkRuntimePermissions();
        boolean isBatteryWhitelisted = isBatteryOptimizationIgnored();
        boolean isRunning = MediaSessionService.isRunning();

        // 1. Notification Listener Access Status
        if (!hasNotificationAccess) {
            tvPermission.setText("NOTIFICATION ACCESS: NOT GRANTED");
            tvPermission.setTextColor(Color.parseColor("#FF6B35"));
            btnPermission.setVisibility(View.VISIBLE);
        } else {
            tvPermission.setText("NOTIFICATION ACCESS: GRANTED");
            tvPermission.setTextColor(Color.parseColor("#00FF88"));
            btnPermission.setVisibility(View.GONE);
        }

        // Show restricted settings button on Android 13+ if notification access not yet granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationAccess) {
            btnRestrictedSettings.setVisibility(View.VISIBLE);
        } else {
            btnRestrictedSettings.setVisibility(View.GONE);
        }

        // 2. Runtime Permissions Status
        if (hasRuntimePerms) {
            tvRuntimePerms.setText("PHONE PERMISSIONS: ALL GRANTED");
            tvRuntimePerms.setTextColor(Color.parseColor("#00FF88"));
            btnRuntimePerms.setVisibility(View.GONE);
        } else {
            tvRuntimePerms.setText("PHONE PERMISSIONS: MISSING (BLUETOOTH / NOTIF / LOCATION)");
            tvRuntimePerms.setTextColor(Color.parseColor("#FF6B35"));
            btnRuntimePerms.setVisibility(View.VISIBLE);
        }

        // 3. Battery Optimization Status
        if (isBatteryWhitelisted) {
            tvBatteryStatus.setText("BATTERY STATUS: UNRESTRICTED (OPTIMAL)");
            tvBatteryStatus.setTextColor(Color.parseColor("#00FF88"));
            btnBatteryOptimization.setVisibility(View.GONE);
        } else {
            tvBatteryStatus.setText("BATTERY STATUS: OPTIMIZED (BACKGROUND KILL RISK)");
            tvBatteryStatus.setTextColor(Color.parseColor("#FFAA00"));
            btnBatteryOptimization.setVisibility(View.VISIBLE);
        }

        // 4. Service Running Status & Buttons
        if (isRunning) {
            tvStatus.setText("STATUS: SERVICE RUNNING - READY FOR GOOGLE MAPS");
            tvStatus.setTextColor(Color.parseColor("#00FF88"));
            btnStartService.setEnabled(false);
            btnStopService.setEnabled(true);
        } else {
            tvStatus.setText("STATUS: STOPPED");
            tvStatus.setTextColor(Color.parseColor("#AAAAAA"));
            btnStartService.setEnabled(hasNotificationAccess);
            btnStopService.setEnabled(false);
        }
    }

    private boolean isNotificationListenerEnabled() {
        Set<String> enabled = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabled.contains(getPackageName());
    }

    private boolean checkRuntimePermissions() {
        List<String> needed = getMissingPermissions();
        return needed.isEmpty();
    }

    private List<String> getMissingPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        return permissions;
    }

    private void requestRequiredPermissions() {
        List<String> missing = getMissingPermissions();
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missing.toArray(new String[0]),
                    PERMISSION_REQ_CODE);
        } else {
            Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show();
            updateUI();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            updateUI();
        }
    }

    private boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return false;
    }

    @SuppressLint("BatteryLife")
    private void requestIgnoreBatteryOptimization() {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                // Fallback to battery optimization settings list
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(fallback);
            } catch (Exception ex) {
                Toast.makeText(this, "Please disable battery optimization in App Info -> Battery", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openAppDetailsSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
            Toast.makeText(this, "Tap top-right (⋮) -> 'Allow restricted settings'", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open App Info", Toast.LENGTH_SHORT).show();
        }
    }
}
