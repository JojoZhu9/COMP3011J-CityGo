package com.example.citygo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RouteOptimizer {

    // Computes the Haversine distance in meters between two LatLonPoints
    private static float distance(LatLonPoint a, LatLonPoint b) {
        if (a == null || b == null) return Float.MAX_VALUE;

        double lat1 = Math.toRadians(a.getLatitude());
        double lng1 = Math.toRadians(a.getLongitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double lng2 = Math.toRadians(b.getLongitude());

        double dLat = lat2 - lat1;
        double dLng = lng2 - lng1;

        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);

        double aa = sinLat * sinLat +
                Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;

        double c = 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(1 - aa));
        double earthRadius = 6371000.0; // meters

        return (float) (earthRadius * c);
    }

    // Greedy nearest-neighbor algorithm for reordering a list of attraction names into a plausible route
    public static List<String> findOptimalRoute(List<String> points,
                                                Map<String, LatLonPoint> attractionPoints) {

        if (points == null || points.size() <= 1) return points;

        List<String> unvisited = new ArrayList<>(points);
        List<String> route = new ArrayList<>();

        // Start at the first attraction in the list
        String current = unvisited.remove(0);
        route.add(current);

        while (!unvisited.isEmpty()) {

            String nearest = null;
            float minDistance = Float.MAX_VALUE;
            LatLonPoint pCurrent = attractionPoints.get(current);

            if (pCurrent == null) {
                // If current point has no coordinates, skip it and continue
                current = unvisited.remove(0);
                route.add(current);
                continue;
            }

            int nearestIndex = -1;
            for (int i = 0; i < unvisited.size(); i++) {
                String candidate = unvisited.get(i);
                LatLonPoint pCandidate = attractionPoints.get(candidate);
                float d = distance(pCurrent, pCandidate);

                if (d < minDistance) {
                    minDistance = d;
                    nearest = candidate;
                    nearestIndex = i;
                }
            }

            if (nearest != null) {
                current = nearest;
                unvisited.remove(nearestIndex);
                route.add(current);
            } else {
                // All remaining points have no coordinates → append them directly
                route.addAll(unvisited);
                break;
            }
        }
        return route;
    }

    // Global nearest-neighbor sorting: creates an ordered list covering all attractions
    public static List<String> sortAttractionsByNearestNeighbor(List<String> attractions,
                                                                Map<String, LatLonPoint> attractionPoints) {
        if (attractions == null || attractions.size() < 2) return attractions;

        List<String> sortedList = new ArrayList<>();
        List<String> unvisited = new ArrayList<>(attractions);

        // Start with the first attraction in the list
        String currentAttraction = unvisited.get(0);
        sortedList.add(currentAttraction);
        unvisited.remove(currentAttraction);

        // Repeatedly pick the closest next attraction
        while (!unvisited.isEmpty()) {
            final String finalCurrent = currentAttraction;

            String nearest = Collections.min(unvisited, (a1, a2) -> {
                LatLonPoint p0 = attractionPoints.get(finalCurrent);
                LatLonPoint p1 = attractionPoints.get(a1);
                LatLonPoint p2 = attractionPoints.get(a2);

                if (p0 == null || p1 == null || p2 == null) {
                    // If any point is missing coordinates, keep the original order
                    return 0;
                }

                float d1 = distance(p0, p1);
                float d2 = distance(p0, p2);
                return Float.compare(d1, d2);
            });

            currentAttraction = nearest;
            sortedList.add(currentAttraction);
            unvisited.remove(currentAttraction);
        }

        return sortedList;
    }
}
