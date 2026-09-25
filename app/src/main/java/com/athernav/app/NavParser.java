package com.athernav.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Google Maps navigation notification text into
 * Ather dashboard compatible strings.
 *
 * Display constraints:
 * - Max 11 chars visible at a time (scrolls)
 * - Allowed: A-Z, 0-9, hyphen (-), space
 * - Uppercase only
 */
public class NavParser {

    // Regex to extract distance: "200 m", "1.2 km", "500m", "in 200 m"
    private static final Pattern DISTANCE_PATTERN =
        Pattern.compile("(\\d+\\.?\\d*)\\s*(km|m)\\b", Pattern.CASE_INSENSITIVE);

    public static NavInfo parseFull(String title, String text, String subText) {
        NavInfo info = new NavInfo();
        if (title == null) title = "";
        if (text == null) text = "";
        if (subText == null) subText = "";

        info.turnInstruction = parse(title, text);
        info.distanceMeters = extractDistanceMeters(title + " " + text);

        String all = (title + " " + text + " " + subText).trim();
        info.etaTime = extractEtaTime(all);
        info.remainingDist = extractRemainingDistance(text + " " + subText);
        info.remainingTime = extractRemainingTime(text + " " + subText);
        info.trafficAlert = extractTrafficAlert(all);
        info.speedCamera = extractSpeedCameraAlert(all);

        return info;
    }

    public static String parse(String title, String text) {
        if (title == null) title = "";
        if (text == null) text = "";

        String combined = (title + " " + text).toLowerCase().trim();

        // Navigation ended
        if (combined.contains("arrived") || combined.contains("destination")) {
            if (combined.contains("left")) return "DEST-LEFT";
            if (combined.contains("right")) return "DEST-RIGHT";
            return "ARRIVED";
        }

        // Rerouting
        if (combined.contains("rerouting") || combined.contains("recalculating")) {
            return "REROUTING";
        }

        // Extract distance first
        String distance = extractDistance(combined);

        // Detect maneuver
        String maneuver = detectManeuver(combined);

        // Combine
        if (!distance.isEmpty()) {
            return (distance + " " + maneuver).trim();
        }
        return maneuver;
    }

    private static String detectManeuver(String text) {

        // Ferry
        if (text.contains("ferry") && text.contains("train")) return "FERRY-TRAIN";
        if (text.contains("ferry")) return "TAKE-FERRY";

        // Roundabout
        boolean isRoundabout = text.contains("roundabout") || text.contains("rotary") || text.contains("traffic circle");
        String exitNum = extractRoundaboutExit(text);
        if (isRoundabout || (exitNum != null && text.contains("exit"))) {
            if (exitNum != null) return "RNBT-EXIT-" + exitNum;
            if (text.contains("exit")) return "RNBT-EXIT";
            if (text.contains("left")) return "RNBT-L";
            return "RNBT-R";
        }

        // U-turn
        if (text.contains("u-turn") || text.contains("uturn") || text.contains("u turn")) {
            if (text.contains("left")) return "U-TURN-L";
            return "U-TURN-R";
        }

        // Merge
        if (text.contains("merge")) {
            if (text.contains("left")) return "MERGE-L";
            return "MERGE-R";
        }

        // Exit highway
        if (text.contains("exit") && (text.contains("highway") || text.contains("freeway") || text.contains("motorway"))) {
            if (text.contains("left")) return "EXIT-LEFT";
            return "EXIT-RIGHT";
        }

        // Ramp
        if (text.contains("ramp")) {
            if (text.contains("slight left") || text.contains("keep left")) return "RAMP-SL";
            if (text.contains("slight right") || text.contains("keep right")) return "RAMP-SR";
            if (text.contains("left")) return "RAMP-LEFT";
            return "RAMP-RIGHT";
        }

        // Keep
        if (text.contains("keep left") || text.contains("stay left")) return "KEEP-LEFT";
        if (text.contains("keep right") || text.contains("stay right")) return "KEEP-RIGHT";

        // Enter highway
        if (text.contains("highway") || text.contains("freeway") || text.contains("motorway")) {
            return "ENTER-HWY";
        }

        // Sharp turns
        if (text.contains("sharp left") || text.contains("hard left")) return "TURN-HL";
        if (text.contains("sharp right") || text.contains("hard right")) return "TURN-HR";

        // Slight turns
        if (text.contains("slight left") || text.contains("bear left")) return "TURN-SL";
        if (text.contains("slight right") || text.contains("bear right")) return "TURN-SR";

        // Basic turns
        if (text.contains("turn left") || (text.contains("left") && !text.contains("right"))) return "TURN-LEFT";
        if (text.contains("turn right") || (text.contains("right") && !text.contains("left"))) return "TURN-RIGHT";

        // Straight
        if (text.contains("straight") || text.contains("continue") || text.contains("head")) {
            return "GO-STRAIGHT";
        }

        // Name change
        if (text.contains("name change") || text.contains("becomes") || text.contains("road changes")) {
            return "NAME-CHANGE";
        }

        // Depart
        if (text.contains("depart") || text.contains("start") || text.contains("head out")) {
            return "DEPART-NOW";
        }

        // Fallback: sanitize raw text
        return sanitize(text.length() > 11 ? text.substring(0, 11) : text);
    }

    private static String extractRoundaboutExit(String text) {
        if (text == null || text.isEmpty()) return null;

        // 1. Check numeric ordinal: "1st exit", "2nd exit", "3rd exit", "4th exit", "5th exit", "1 exit", etc.
        Matcher m1 = Pattern.compile("\\b(\\d+)(?:st|nd|rd|th)?\\s+exit\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m1.find()) {
            return m1.group(1);
        }

        // 2. Check "take the 1st", "take the 2nd", "take the 3rd", etc.
        Matcher m2 = Pattern.compile("\\btake\\s+the\\s+(\\d+)(?:st|nd|rd|th)?\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m2.find()) {
            return m2.group(1);
        }

        // 3. Check "exit 1", "exit no. 2", "exit #3"
        Matcher m3 = Pattern.compile("\\bexit\\s+(?:no\\.?\\s+|#\\s*)?(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m3.find()) {
            return m3.group(1);
        }

        // 4. Word numbers: "first exit", "second exit", "third exit", "fourth exit", "fifth exit", etc.
        if (text.contains("first exit")  || text.contains("1st exit") || text.contains("pehla exit")  || text.contains("take the first"))  return "1";
        if (text.contains("second exit") || text.contains("2nd exit") || text.contains("doosra exit") || text.contains("take the second")) return "2";
        if (text.contains("third exit")  || text.contains("3rd exit") || text.contains("teesra exit") || text.contains("take the third"))  return "3";
        if (text.contains("fourth exit") || text.contains("4th exit") || text.contains("chautha exit")|| text.contains("take the fourth")) return "4";
        if (text.contains("fifth exit")  || text.contains("5th exit") || text.contains("paanchva exit")|| text.contains("take the fifth")) return "5";
        if (text.contains("sixth exit")  || text.contains("6th exit") || text.contains("take the sixth"))  return "6";

        return null;
    }

    private static String extractDistance(String text) {
        Matcher m = DISTANCE_PATTERN.matcher(text);
        if (m.find()) {
            String value = m.group(1);
            String unit = m.group(2).toLowerCase();

            if (unit.equals("km")) {
                // Replace dot with hyphen: 1.2 → 1-2
                value = value.replace(".", "-");
                return value + "KM";
            } else {
                // Meters: just number + M
                // Remove decimal if present
                if (value.contains(".")) {
                    value = value.substring(0, value.indexOf("."));
                }
                return value + "M";
            }
        }
        return "";
    }

    public static int extractDistanceMeters(String text) {
        if (text == null) return -1;
        Matcher m = DISTANCE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String unit = m.group(2).toLowerCase();
                if ("km".equals(unit)) {
                    return (int) (val * 1000);
                } else {
                    return (int) val;
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    public static String extractEtaTime(String text) {
        if (text == null) return "";
        // Match 12-hour or 24-hour time e.g. 6:45 PM, 18:45
        Pattern p = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\s*([ap]m)?\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String time = m.group(1).replace(":", "-");
            String ampm = m.group(2) != null ? m.group(2).toUpperCase() : "";
            return sanitize("ETA " + time + ampm);
        }
        return "";
    }

    public static String extractRemainingDistance(String text) {
        if (text == null) return "";
        Matcher m = DISTANCE_PATTERN.matcher(text);
        if (m.find()) {
            String val = m.group(1).replace(".", "-");
            String unit = m.group(2).toUpperCase();
            return sanitize("REM " + val + unit);
        }
        return "";
    }

    public static String extractRemainingTime(String text) {
        if (text == null) return "";
        Pattern p = Pattern.compile("(\\d+)\\s*(?:hr|h)\\s*(\\d+)?\\s*(?:min|m)?|(\\d+)\\s*(?:min|mins)\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            if (m.group(1) != null) {
                String hr = m.group(1);
                String min = m.group(2) != null ? m.group(2) : "0";
                return sanitize("REM " + hr + "H" + min + "M");
            } else if (m.group(3) != null) {
                return sanitize("REM " + m.group(3) + "MIN");
            }
        }
        return "";
    }

    public static String extractTrafficAlert(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase();

        // 1. Min slower or delay
        Pattern p = Pattern.compile("(\\d+)\\s*(?:min|mins|m)?\\s*(?:slower|delay)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return sanitize("JAM +" + m.group(1) + "MIN");
        }

        // 2. Accident
        if (lower.contains("accident")) {
            return "ACCIDENT-AHD";
        }

        // 3. Heavy / Slow Traffic
        if (lower.contains("heavy traffic") || lower.contains("congestion")) {
            return "HEAVY-TRAFFIC";
        }
        if (lower.contains("slow traffic")) {
            return "SLOW-TRAFFIC";
        }

        return "";
    }

    public static String extractSpeedCameraAlert(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase();
        if (lower.contains("speed camera") || lower.contains("camera ahead") || lower.contains("speed trap")) {
            Matcher m = DISTANCE_PATTERN.matcher(text);
            if (m.find()) {
                String d = m.group(1).replace(".", "-") + m.group(2).toUpperCase();
                return sanitize("CAM " + d);
            }
            return "CAMERA-AHD";
        }
        return "";
    }

    /**
     * Sanitize string to only allowed characters: A-Z, 0-9, hyphen, space
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return input.toUpperCase().replaceAll("[^A-Z0-9\\- ]", "").trim();
    }
}
