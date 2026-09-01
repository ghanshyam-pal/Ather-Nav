package com.athernav.app;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches weather from wttr.in - no API key required
 * Updates every 5 minutes
 */
public class WeatherFetcher {

    public interface WeatherCallback {
        void onWeatherUpdate(String dashboardText);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WeatherCallback callback;
    private String lastWeather = "FETCHING";
    private Runnable refreshRunnable;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_INTERVAL = 5 * 60 * 1000; // 5 minutes

    public void start(WeatherCallback cb) {
        this.callback = cb;
        fetch();
        // Schedule refresh every 5 min
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetch();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    public void stop() {
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        executor.shutdown();
    }

    public String getLastWeather() {
        return lastWeather;
    }

    private void fetch() {
        executor.execute(() -> {
            try {
                // wttr.in format: ?format=%t+%C
                // %t = temperature, %C = condition
                // Using format=j1 for JSON to get more data
                URL url = new URL("https://wttr.in/?format=%t+%c+%p");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "AtherNav/1.0");

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                String line = reader.readLine();
                reader.close();

                if (line != null) {
                    String formatted = parseWeather(line.trim());
                    lastWeather = formatted;
                    mainHandler.post(() -> {
                        if (callback != null) callback.onWeatherUpdate(formatted);
                    });
                }
            } catch (Exception e) {
                lastWeather = "WTHR-ERROR";
                mainHandler.post(() -> {
                    if (callback != null) callback.onWeatherUpdate("WTHR-ERROR");
                });
            }
        });
    }

    private String parseWeather(String raw) {
        // wttr.in returns like: +32°C ☀️ 0.0mm
        // We need: 32C-SUNNY or 32C-RAIN
        try {
            // Extract temperature number
            String temp = raw.replaceAll("[^0-9+\\-].*", "").trim();
            if (temp.startsWith("+")) temp = temp.substring(1);

            // Detect condition from emoji/text
            String condition = "CLEAR";
            String lower = raw.toLowerCase();
            if (lower.contains("rain") || raw.contains("🌧") || raw.contains("🌦") || raw.contains("⛈")) {
                condition = "RAIN";
            } else if (lower.contains("cloud") || raw.contains("☁") || raw.contains("🌥")) {
                condition = "CLOUDY";
            } else if (lower.contains("snow") || raw.contains("❄") || raw.contains("🌨")) {
                condition = "SNOW";
            } else if (lower.contains("storm") || raw.contains("⛈")) {
                condition = "STORM";
            } else if (lower.contains("fog") || raw.contains("🌫")) {
                condition = "FOGGY";
            } else if (raw.contains("☀") || raw.contains("🌞")) {
                condition = "SUNNY";
            } else if (raw.contains("🌤") || raw.contains("⛅")) {
                condition = "PTCLOUD";
            }

            // Check precipitation
            String precip = "";
            if (raw.contains("mm")) {
                String[] parts = raw.split(" ");
                for (String p : parts) {
                    if (p.contains("mm") && !p.equals("0.0mm")) {
                        precip = "-RAIN";
                        condition = "RAIN";
                        break;
                    }
                }
            }

            return NavParser.sanitize(temp + "C-" + condition);

        } catch (Exception e) {
            return NavParser.sanitize(raw.length() > 11 ? raw.substring(0, 11) : raw);
        }
    }
}
