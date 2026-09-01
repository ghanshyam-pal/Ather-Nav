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
        if (text.contains("roundabout") || text.contains("rotary") || text.contains("traffic circle")) {
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

    /**
     * Sanitize string to only allowed characters: A-Z, 0-9, hyphen, space
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return input.toUpperCase().replaceAll("[^A-Z0-9\\- ]", "").trim();
    }
}
