package com.athernav.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvDashboard;
    private TextView tvPermission;
    private Button btnPermission;
    private Button btnStartService;
    private Button btnStopService;
    private Button btnSwitchMode;

    private boolean serviceRunning = false;

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
        tvPermission = findViewById(R.id.tvPermission);
        btnPermission = findViewById(R.id.btnPermission);
        btnStartService = findViewById(R.id.btnStartService);
        btnStopService = findViewById(R.id.btnStopService);
        btnSwitchMode = findViewById(R.id.btnSwitchMode);

        btnSwitchMode.setOnClickListener(v -> {
            Intent intent = new Intent(this, MediaSessionService.class);
            intent.setAction(MediaSessionService.ACTION_SWITCH_MODE);
            startService(intent);
        });

        btnPermission.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });

        btnStartService.setOnClickListener(v -> {
            Intent intent = new Intent(this, MediaSessionService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            serviceRunning = true;
            updateUI();
        });

        btnStopService.setOnClickListener(v -> {
            stopService(new Intent(this, MediaSessionService.class));
            serviceRunning = false;
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
        unregisterReceiver(navReceiver);
    }

    private void updateUI() {
        boolean hasPermission = isNotificationListenerEnabled();

        if (!hasPermission) {
            tvPermission.setText("NOTIFICATION ACCESS: NOT GRANTED");
            tvPermission.setTextColor(getColor(android.R.color.holo_red_light));
            btnPermission.setVisibility(View.VISIBLE);
            btnStartService.setEnabled(false);
        } else {
            tvPermission.setText("NOTIFICATION ACCESS: GRANTED");
            tvPermission.setTextColor(getColor(android.R.color.holo_green_light));
            btnPermission.setVisibility(View.GONE);
            btnStartService.setEnabled(true);
        }

        if (serviceRunning) {
            tvStatus.setText("STATUS: SERVICE RUNNING - WAITING FOR MAPS");
            btnStartService.setEnabled(false);
            btnStopService.setEnabled(true);
        } else {
            tvStatus.setText("STATUS: STOPPED");
            btnStartService.setEnabled(hasPermission);
            btnStopService.setEnabled(false);
        }
    }

    private boolean isNotificationListenerEnabled() {
        Set<String> enabled = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabled.contains(getPackageName());
    }
}
