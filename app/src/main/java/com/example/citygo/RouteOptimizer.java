package com.example.citygo;

import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.model.LatLng;
import com.amap.api.services.core.LatLonPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RouteOptimizer {

    // 贪心算法：查找最近邻路线
    public static List<String> findOptimalRoute(List<String> points, Map<String, LatLonPoint> attractionPoints) {
        if (points == null || points.size() <= 1) return points;

        List<String> unvisited = new ArrayList<>(points);
        List<String> route = new ArrayList<>();

        // 起点：默认取列表第一个
        String current = unvisited.remove(0);
        route.add(current);

        while (!unvisited.isEmpty()) {
            String nearest = null;
            float minDistance = Float.MAX_VALUE;
            LatLonPoint pCurrent = attractionPoints.get(current);

            if (pCurrent == null) {
                current = unvisited.remove(0);
                route.add(current);
                continue;
            }

            int nearestIndex = -1;
            for (int i = 0; i < unvisited.size(); i++) {
                String candidate = unvisited.get(i);
                LatLonPoint pCandidate = attractionPoints.get(candidate);
                if (pCandidate != null) {
                    float distance = AMapUtils.calculateLineDistance(
                            new LatLng(pCurrent.getLatitude(), pCurrent.getLongitude()),
                            new LatLng(pCandidate.getLatitude(), pCandidate.getLongitude())
                    );
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearest = candidate;
                        nearestIndex = i;
                    }
                }
            }

            if (nearest != null) {
                current = nearest;
                unvisited.remove(nearestIndex);
                route.add(current);
            } else {
                route.addAll(unvisited);
                break;
            }
        }
        return route;
    }

    // 全局排序：将所有点连成一条线
    public static List<String> sortAttractionsByNearestNeighbor(List<String> attractions, Map<String, LatLonPoint> attractionPoints) {
        if (attractions.size() < 2) return attractions;
        List<String> sortedList = new ArrayList<>();
        List<String> unvisited = new ArrayList<>(attractions);
        String currentAttraction = unvisited.get(0);
        sortedList.add(currentAttraction);
        unvisited.remove(currentAttraction);

        while (!unvisited.isEmpty()) {
            final String finalCurrent = currentAttraction;
            String nearest = Collections.min(unvisited, (a1, a2) -> {
                LatLonPoint p0 = attractionPoints.get(finalCurrent);
                LatLonPoint p1 = attractionPoints.get(a1);
                LatLonPoint p2 = attractionPoints.get(a2);
                if(p0==null || p1==null || p2==null) return 0;

                float d1 = AMapUtils.calculateLineDistance(new LatLng(p0.getLatitude(), p0.getLongitude()), new LatLng(p1.getLatitude(), p1.getLongitude()));
                float d2 = AMapUtils.calculateLineDistance(new LatLng(p0.getLatitude(), p0.getLongitude()), new LatLng(p2.getLatitude(), p2.getLongitude()));
                return Float.compare(d1, d2);
            });
            currentAttraction = nearest;
            sortedList.add(currentAttraction);
            unvisited.remove(currentAttraction);
        }
        return sortedList;
    }
}