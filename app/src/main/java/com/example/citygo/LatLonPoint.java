package com.example.citygo;

public class LatLonPoint {
    private final double latitude;
    private final double longitude;

    public LatLonPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
