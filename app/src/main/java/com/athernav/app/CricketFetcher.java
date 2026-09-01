package com.athernav.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches live cricket scores from CricAPI (cricapi.com)
 * Requires a free API key from https://cricapi.com/
 *
 * Cycles through: Team scores -> Match status -> (repeat)
 */
public class CricketFetcher {

    public interface CricketCallback {
        void onScoreUpdate(String dashboardText);
    }

    // Get your free key at https://cricapi.com/ and paste it here
    private static final String API_KEY = "YOUR_CRICAPI_KEY_HERE";
    private static final String URL_CURRENT_MATCHES =
        "https://api.cricapi.com/v1/currentMatches?apikey=" + API_KEY + "&offset=0";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 60 * 1000; // 1 minute - live score changes fast

    private CricketCallback callback;
    private String lastScore = "NO-MATCH";
    private int displayToggle = 0; // alternate between score and status

    public void start(CricketCallback cb) {
        this.callback = cb;
        fetch();
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
        if (refreshRunnable != null) refreshHandler.removeCallbacks(refreshRunnable);
        executor.shutdown();
    }

    public String getLastScore() {
        return lastScore;
    }

    private void fetch() {
        if (API_KEY.equals("YOUR_CRICAPI_KEY_HERE")) {
            lastScore = "NO-API-KEY";
            mainHandler.post(() -> {
                if (callback != null) callback.onScoreUpdate("NO-API-KEY");
            });
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(URL_CURRENT_MATCHES);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String formatted = parseLiveMatch(sb.toString());
                lastScore = formatted;
                mainHandler.post(() -> {
                    if (callback != null) callback.onScoreUpdate(formatted);
                });

            } catch (Exception e) {
                lastScore = "SCORE-ERR";
                mainHandler.post(() -> {
                    if (callback != null) callback.onScoreUpdate("SCORE-ERR");
                });
            }
        });
    }

    private String parseLiveMatch(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray matches = root.optJSONArray("data");
            if (matches == null || matches.length() == 0) return "NO-MATCH";

            // Find first live match
            JSONObject liveMatch = null;
            for (int i = 0; i < matches.length(); i++) {
                JSONObject m = matches.getJSONObject(i);
                if (m.optBoolean("matchStarted", false) &&
                    !m.optBoolean("matchEnded", true)) {
                    liveMatch = m;
                    break;
                }
            }

            if (liveMatch == null) {
                // No live match, show next/last match teams
                JSONObject m = matches.getJSONObject(0);
                JSONArray teams = m.optJSONArray("teams");
                if (teams != null && teams.length() >= 2) {
                    String t1 = shortTeamName(teams.getString(0));
                    String t2 = shortTeamName(teams.getString(1));
                    return NavParser.sanitize(t1 + "-VS-" + t2);
                }
                return "NO-MATCH";
            }

            // Parse live match score
            JSONArray teamInfo = liveMatch.optJSONArray("score");
            if (teamInfo == null || teamInfo.length() == 0) {
                JSONArray teams = liveMatch.optJSONArray("teams");
                if (teams != null && teams.length() >= 2) {
                    String t1 = shortTeamName(teams.getString(0));
                    String t2 = shortTeamName(teams.getString(1));
                    return NavParser.sanitize(t1 + "-VS-" + t2);
                }
                return "LIVE-MATCH";
            }

            // Alternate display: score line vs status text
            displayToggle++;
            if (displayToggle % 2 == 0) {
                String status = liveMatch.optString("status", "IN PROGRESS");
                return NavParser.sanitize(status.length() > 11 ? status.substring(0, 11) : status);
            } else {
                // Last innings score: team + runs + wickets + overs
                JSONObject lastInnings = teamInfo.getJSONObject(teamInfo.length() - 1);
                String inning = lastInnings.optString("inning", "");
                int r = lastInnings.optInt("r", 0);
                int w = lastInnings.optInt("w", 0);
                double o = lastInnings.optDouble("o", 0);

                String team = shortTeamName(inning.replace(" Inning 1", "").replace(" Inning 2", ""));
                String scoreLine = team + "-" + r + "-" + w;
                return NavParser.sanitize(scoreLine.length() > 11 ? scoreLine.substring(0, 11) : scoreLine);
            }

        } catch (Exception e) {
            return "PARSE-ERR";
        }
    }

    private String shortTeamName(String fullName) {
        // Take first word or abbreviate common team names
        String name = fullName.trim();
        if (name.length() <= 4) return name.toUpperCase();

        // Common abbreviations
        switch (name.toUpperCase()) {
            case "INDIA": return "IND";
            case "AUSTRALIA": return "AUS";
            case "ENGLAND": return "ENG";
            case "PAKISTAN": return "PAK";
            case "SOUTH AFRICA": return "SA";
            case "NEW ZEALAND": return "NZ";
            case "SRI LANKA": return "SL";
            case "BANGLADESH": return "BAN";
            case "WEST INDIES": return "WI";
            case "AFGHANISTAN": return "AFG";
        }

        // For IPL teams, take first 3-4 letters of first word
        String firstWord = name.split(" ")[0];
        return firstWord.length() > 4 ? firstWord.substring(0, 4).toUpperCase() : firstWord.toUpperCase();
    }
}
