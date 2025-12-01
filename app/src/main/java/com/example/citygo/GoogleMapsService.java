package com.example.citygo;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles all direct HTTP calls to Google Maps Platform:
 * - Geocoding
 * - Places Text Search
 * - Nearby Search
 * - Directions (routes)
 */
public class GoogleMapsService {

    private static final String TAG = "GoogleMapsService";

    private final String apiKey;
    private final ExecutorService executor;

    public GoogleMapsService(String apiKey, ExecutorService executor) {
        this.apiKey = apiKey;
        // Reuse caller executor if provided, otherwise make our own
        this.executor = (executor != null) ? executor : Executors.newSingleThreadExecutor();
    }

    // ------------------ Models & callbacks ------------------

    public interface GeocodeCallback {
        void onResult(boolean success, double lat, double lng);
    }

    public interface AttractionSearchCallback {
        void onResult(boolean success, LatLonPoint point);
    }

    public static class PlaceItem {
        public final String title;
        public final String type;
        public final double lat;
        public final double lng;

        public PlaceItem(String title, String type, double lat, double lng) {
            this.title = title;
            this.type = type;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public interface NearbyCallback {
        void onResult(List<PlaceItem> places);
    }

    public interface RouteCallback {
        void onResult(List<LatLng> path);
    }

    private interface JsonCallback {
        void onResult(JSONObject json) throws Exception;
    }

    // ------------------ Public APIs ------------------

    /** Geocode full city name to lat/lng using Geocoding API */
    public void geocodeCity(String cityName, GeocodeCallback callback) {
        String urlStr = "https://maps.googleapis.com/maps/api/geocode/json?address="
                + urlEncode(cityName)
                + "&key=" + apiKey;

        runGet(urlStr, json -> {
            boolean success = "OK".equals(json.optString("status"));
            if (!success) {
                Log.w(TAG, "Geocode failed: " + json.optString("status"));
                callback.onResult(false, 0, 0);
                return;
            }
            JSONObject location = json.getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location");
            double lat = location.getDouble("lat");
            double lng = location.getDouble("lng");
            callback.onResult(true, lat, lng);
        });
    }

    /**
     * Places Text Search: search a single attraction as "name in city",
     * return the first result as LatLonPoint.
     */
    public void searchAttraction(String attractionName,
                                 String city,
                                 AttractionSearchCallback callback) {

        String query = attractionName + " in " + city;
        String urlStr = "https://maps.googleapis.com/maps/api/place/textsearch/json?query="
                + urlEncode(query)
                + "&key=" + apiKey;

        runGet(urlStr, json -> {
            boolean success = false;
            LatLonPoint point = null;

            if ("OK".equals(json.optString("status"))) {
                JSONArray results = json.optJSONArray("results");
                if (results != null && results.length() > 0) {
                    JSONObject first = results.getJSONObject(0);
                    JSONObject loc = first.getJSONObject("geometry").getJSONObject("location");
                    double lat = loc.getDouble("lat");
                    double lng = loc.getDouble("lng");
                    point = new LatLonPoint(lat, lng);
                    success = true;
                }
            }
            callback.onResult(success, point);
        });
    }

    /** Nearby search for tourist attractions within 500m */
    public void searchNearbyAttractions(double centerLat,
                                        double centerLng,
                                        NearbyCallback callback) {

        String urlStr = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
                + "?location=" + centerLat + "," + centerLng
                + "&radius=500"
                + "&type=tourist_attraction"
                + "&key=" + apiKey;

        runGet(urlStr, json -> {
            List<PlaceItem> places = new ArrayList<>();

            if ("OK".equals(json.optString("status"))) {
                JSONArray results = json.optJSONArray("results");
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject obj = results.getJSONObject(i);
                        String name = obj.optString("name");
                        String type = "Place";
                        JSONArray typesArr = obj.optJSONArray("types");
                        if (typesArr != null && typesArr.length() > 0) {
                            type = typesArr.getString(0);
                        }
                        JSONObject loc = obj.getJSONObject("geometry").getJSONObject("location");
                        double lat = loc.getDouble("lat");
                        double lng = loc.getDouble("lng");
                        places.add(new PlaceItem(name, type, lat, lng));
                    }
                }
            }
            callback.onResult(places);
        });
    }

    /** Directions API: driving route with optional waypoints */
    public void fetchRoute(LatLonPoint from,
                           LatLonPoint to,
                           List<LatLonPoint> passby,
                           RouteCallback callback) {

        StringBuilder urlBuilder = new StringBuilder(
                "https://maps.googleapis.com/maps/api/directions/json?");
        urlBuilder.append("origin=").append(from.getLatitude())
                .append(",").append(from.getLongitude());
        urlBuilder.append("&destination=").append(to.getLatitude())
                .append(",").append(to.getLongitude());

        if (passby != null && !passby.isEmpty()) {
            urlBuilder.append("&waypoints=");
            for (int i = 0; i < passby.size(); i++) {
                LatLonPoint p = passby.get(i);
                urlBuilder.append(p.getLatitude())
                        .append(",").append(p.getLongitude());
                if (i < passby.size() - 1) urlBuilder.append("|");
            }
        }
        urlBuilder.append("&mode=driving&key=").append(apiKey);

        runGet(urlBuilder.toString(), json -> {
            List<LatLng> path = new ArrayList<>();
            if (!"OK".equals(json.optString("status"))) {
                callback.onResult(path);
                return;
            }
            JSONArray routes = json.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                callback.onResult(path);
                return;
            }
            JSONObject route = routes.getJSONObject(0);
            JSONObject overview = route.getJSONObject("overview_polyline");
            String encoded = overview.getString("points");
            path.addAll(decodePolyline(encoded));
            callback.onResult(path);
        });
    }

    // ------------------ Internal helpers ------------------

    private void runGet(String urlStr, JsonCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                JSONObject json = new JSONObject(sb.toString());
                callback.onResult(json);
            } catch (Exception e) {
                Log.e(TAG, "HTTP error for " + urlStr, e);
                try {
                    callback.onResult(new JSONObject()); // fail-safe empty object
                } catch (Exception ignored) {}
            }
        });
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // Polyline decode helper
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double latD = lat / 1E5;
            double lngD = lng / 1E5;
            poly.add(new LatLng(latD, lngD));
        }
        return poly;
    }
}
