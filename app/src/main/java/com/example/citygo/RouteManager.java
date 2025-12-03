package com.example.citygo;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RouteManager {

    private static final String TAG = "RouteManager";
    private GoogleMap map;
    private String apiKey;
    private Polyline currentPolyline;

    public RouteManager(GoogleMap map, String apiKey) {
        this.map = map;
        this.apiKey = apiKey;
    }

    /**
     * Request a route from Google Directions API
     * @param originLatLng Start point
     * @param destLatLng End point
     * @param mode "driving", "walking", or "transit"
     * @param callback Callback
     */
    public void requestRoute(LatLng originLatLng, LatLng destLatLng, String mode, RouteCallBack callback) {
        new Thread(() -> {
            try {
                // Build URL correctly
                String urlStr =
                        "https://maps.googleapis.com/maps/api/directions/json?"
                                + "origin=" + originLatLng.latitude + "," + originLatLng.longitude
                                + "&destination=" + originLatLng.latitude + "," + originLatLng.longitude
                                + "&waypoints=via:" + destLatLng.latitude + "," + destLatLng.longitude
                                + "&mode=" + mode
                                + "&key=" + apiKey;



                Log.d(TAG, "Directions URL: " + urlStr);

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                JSONObject json = new JSONObject(result.toString());

                if (!json.getString("status").equals("OK")) {
                    postError(callback, "Directions API error: " + json.getString("status"));
                    return;
                }

                JSONArray routes = json.getJSONArray("routes");
                JSONObject route = routes.getJSONObject(0);

                // Extract the polyline
                String encodedPolyline = route
                        .getJSONObject("overview_polyline")
                        .getString("points");

                List<LatLng> decodedPath = PolyUtil.decode(encodedPolyline);

                drawRoute(decodedPath, callback);

            } catch (Exception e) {
                e.printStackTrace();
                postError(callback, "Exception: " + e.getMessage());
            }
        }).start();
    }

    public List<LatLng> syncRequestRoute(LatLng origin, LatLng dest, String mode) {
        try {
            String urlStr = "https://maps.googleapis.com/maps/api/directions/json?"
                    + "origin=" + origin.latitude + "," + origin.longitude
                    + "&destination=" + dest.latitude + "," + dest.longitude
                    + "&mode=" + mode
                    + "&key=" + apiKey;

            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            JSONObject json = new JSONObject(result.toString());

            if (!json.getString("status").equals("OK")) {
                return null;
            }

            JSONArray routes = json.getJSONArray("routes");
            JSONObject route = routes.getJSONObject(0);

            String encoded = route
                    .getJSONObject("overview_polyline")
                    .getString("points");

            return PolyUtil.decode(encoded);

        } catch (Exception e) {
            return null;
        }
    }


    /** Draw polyline on the map */
    private void drawRoute(List<LatLng> path, RouteCallBack callback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (currentPolyline != null) {
                currentPolyline.remove();
            }

            currentPolyline = map.addPolyline(
                    new PolylineOptions()
                            .addAll(path)
                            .width(15f)
            );

            callback.onRouteReady(currentPolyline);
        });
    }

    private void postError(RouteCallBack callback, String message) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onRouteError(message));
    }
}
