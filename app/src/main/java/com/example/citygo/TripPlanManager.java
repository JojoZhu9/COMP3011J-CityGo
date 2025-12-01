package com.example.citygo;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure data & routing logic for a trip:
 * - stores attraction points
 * - distributes them across days
 * - keeps daily plans (names + LatLonPoint)
 * No Android UI code here.
 */
public class TripPlanManager {

    private final int totalDays;
    private final List<String> originalAttractionNames;

    private final Map<String, LatLonPoint> attractionPoints = new HashMap<>();
    private final Map<Integer, List<LatLonPoint>> dailyPlans = new HashMap<>();
    private final Map<Integer, List<String>> dailyPlanNames = new HashMap<>();

    public TripPlanManager(List<String> originalAttractionNames, int totalDays) {
        this.originalAttractionNames = new ArrayList<>(originalAttractionNames);
        this.totalDays = totalDays;
    }

    // ---------- basic getters ----------

    public List<String> getOriginalAttractionNames() {
        return new ArrayList<>(originalAttractionNames);
    }

    public boolean hasAnyPoints() {
        return !attractionPoints.isEmpty();
    }

    public List<String> getMissingAttractions() {
        List<String> missing = new ArrayList<>();
        for (String name : originalAttractionNames) {
            if (!attractionPoints.containsKey(name)) missing.add(name);
        }
        return missing;
    }

    public List<String> getDayNames(int day) {
        List<String> list = dailyPlanNames.get(day);
        return (list != null) ? new ArrayList<>(list) : new ArrayList<>();
    }

    public List<LatLonPoint> getDayPoints(int day) {
        List<LatLonPoint> list = dailyPlans.get(day);
        return (list != null) ? new ArrayList<>(list) : new ArrayList<>();
    }

    public boolean dayContains(int day, String title) {
        List<String> names = dailyPlanNames.get(day);
        return names != null && names.contains(title);
    }

    // ---------- mutate attractions ----------

    /** Called when geocoder / places search successfully finds a point. */
    public void addAttractionPoint(String name, LatLonPoint point) {
        attractionPoints.put(name, point);
    }

    /** Add a POI to a given day and recompute the optimal route for that day. */
    public void addPoiToDay(int day, String title, LatLonPoint point) {
        attractionPoints.put(title, point);

        List<String> names = dailyPlanNames.get(day);
        if (names == null) names = new ArrayList<>();

        if (!names.contains(title)) {
            names.add(title);
        }

        List<String> optimal = RouteOptimizer.findOptimalRoute(names, attractionPoints);
        setDayFromNames(day, optimal);
    }

    /** Remove an attraction from a day route at a position and recompute route. */
    public void removeAttraction(int day, int position) {
        List<String> names = dailyPlanNames.get(day);
        if (names == null || position < 0 || position >= names.size()) return;

        names.remove(position);
        List<String> optimal = RouteOptimizer.findOptimalRoute(names, attractionPoints);
        setDayFromNames(day, optimal);
    }

    /** Reorder a day by a new list of names (used for drag & drop) */
    public void reorderDay(int day, List<String> newOrder) {
        setDayFromNames(day, newOrder);
    }

    // ---------- distribution across days ----------

    /** Called once after all points are known, to distribute them into days. */
    public void distributeAttractionsToDays() {
        dailyPlans.clear();
        dailyPlanNames.clear();

        if (attractionPoints.isEmpty() || totalDays <= 0) return;

        List<String> sortedAttractions = RouteOptimizer.sortAttractionsByNearestNeighbor(
                new ArrayList<>(attractionPoints.keySet()), attractionPoints);

        int attractionsPerDay = (int) Math.ceil((double) sortedAttractions.size() / totalDays);

        for (int i = 0; i < totalDays; i++) {
            int day = i + 1;
            int startIndex = i * attractionsPerDay;
            int endIndex = Math.min(startIndex + attractionsPerDay, sortedAttractions.size());

            if (startIndex < sortedAttractions.size()) {
                List<String> dayNames = new ArrayList<>(sortedAttractions.subList(startIndex, endIndex));
                List<String> optimalRoute =
                        RouteOptimizer.findOptimalRoute(dayNames, attractionPoints);
                setDayFromNames(day, optimalRoute);
            } else {
                dailyPlans.put(day, new ArrayList<>());
                dailyPlanNames.put(day, new ArrayList<>());
            }
        }
    }

    // ---------- helpers ----------

    private void setDayFromNames(int day, List<String> names) {
        List<String> copyNames = new ArrayList<>(names);
        List<LatLonPoint> points = new ArrayList<>();
        for (String n : copyNames) {
            LatLonPoint p = attractionPoints.get(n);
            if (p != null) {
                points.add(p);
            }
        }
        dailyPlanNames.put(day, copyNames);
        dailyPlans.put(day, points);
    }

    public LatLonPoint getPointForAttraction(String name) {
        return attractionPoints.get(name);
    }
}
