package com.example.citygo;

import com.google.android.gms.maps.model.Polyline;

// Callback to return results to MapActivity
public interface RouteCallBack {
    void onRouteReady(Polyline polyline);

    void onRouteError(String message);
}
