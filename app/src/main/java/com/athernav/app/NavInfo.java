package com.athernav.app;

/**
 * Encapsulates parsed Google Maps navigation data,
 * including turn maneuvers, distance in meters, ETA, remaining distance,
 * traffic delays, and speed camera alerts.
 */
public class NavInfo {
    public String turnInstruction = "WAITING";
    public int distanceMeters = -1;
    public String etaTime = "";
    public String remainingDist = "";
    public String remainingTime = "";
    public String trafficAlert = "";
    public String speedCamera = "";

    public boolean hasTurn() {
        return turnInstruction != null && !turnInstruction.isEmpty() && !"WAITING".equals(turnInstruction);
    }

    public boolean isImminentTurn() {
        // If distance is known and <= 300 meters, or approaching maneuver
        return distanceMeters >= 0 && distanceMeters <= 300;
    }
}
