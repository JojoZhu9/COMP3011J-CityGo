package com.example.citygo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.citygo.databinding.ActivityMapBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements

        OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnInfoWindowClickListener,
        AttractionAdapter.OnAttractionActionsListener,
        AttractionAdapter.StartDragListener {

    private static final String TAG = "MapActivity";
    private static final String GOOGLE_MAPS_API_KEY = "AIzaSyAR3DCQQ26plX8A7OUwAVp5lWWr_4hw1yE";
    private GoogleMapsService mapsService;
    private ActivityMapBinding binding;
    private MapView mapView;
    private GoogleMap googleMap;

    // Services & API Helpers
    private DBService dbService;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Data Models
    private String city;
    private int totalDays;
    private String startDateStr;
    private List<String> originalAttractionNames;
    private List<String> attractionsToSearch;
    private TripPlanManager tripPlanManager;
    private TripBudgetController budgetController;
    private AttractionAdapter attractionAdapter;
    private int currentSearchIndex = 0;
    private boolean hasRetried = false;
    private int currentSelectedDay = 1;
    private List<Marker> nearbyPoiMarkers = new ArrayList<>();
    private Map<Marker, GoogleMapsService.PlaceItem> markerPlaceItemMap = new HashMap<>();
    private Map<Marker, String> itineraryMarkerMap = new HashMap<>();
    private ItemTouchHelper itemTouchHelper;
    private LatLng hotelLatLng;
    private String hotelName;
    private Marker hotelMarker;

    private RouteManager routeManager;
    private Polyline currentPolyline;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. Init DB & Preferences
        dbService = new DBService(this);
        UserPrefs prefs = new UserPrefs(this);
        double dailyBudget = prefs.getDailyBudgetCents() / 100.0;
        if (dailyBudget <= 0) dailyBudget = 500;

        // Initialize GoogleMapsService
        mapsService = new GoogleMapsService(GOOGLE_MAPS_API_KEY, executorService);



        // Initialize budget controller
        budgetController = new TripBudgetController(
                this,
                binding,
                dbService,
                dailyBudget
        );

        // 2. Init MapView (Google Maps)
        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);



        setupRecyclerView();
        setupZoomButtons();

        // 3. Parse Intent
        Intent intent = getIntent();
        city = intent.getStringExtra("EXTRA_CITY");
        String attractionsStr = intent.getStringExtra("EXTRA_ATTRACTIONS");
        totalDays = intent.getIntExtra("EXTRA_DAYS", 0);
        startDateStr = intent.getStringExtra("EXTRA_START_DATE");
        hotelName = getIntent().getStringExtra("EXTRA_HOTEL");

        originalAttractionNames = new ArrayList<>();
        if (attractionsStr != null) {
            for (String name : attractionsStr.split("[,，]")) {
                if (!name.trim().isEmpty()) originalAttractionNames.add(name.trim());
            }
        }

        // 4. Validate
        if (totalDays == 0 || originalAttractionNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_incomplete_trip), Toast.LENGTH_LONG).show();
            return;
        }

        tripPlanManager = new TripPlanManager(originalAttractionNames, totalDays);

        // 5. Load Trip ID for Expenses
        dbService.getTrip(city, startDateStr, trip -> {
            if (trip != null) {
                budgetController.setTripId(trip.tripId);
                // day 1 = start date
                runOnUiThread(() ->
                        budgetController.updateBudgetUI(getTargetDateStr(1))
                );
            }
        });

        // 6. UI Bindings
        binding.btnAddExpense.setOnClickListener(v ->
                budgetController.showAddExpenseDialog(getTargetDateStr(currentSelectedDay))
        );
        binding.btnBack.setOnClickListener(v -> finish());
    }

    // Google Maps ready callback
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        routeManager = new RouteManager(googleMap, getString(R.string.google_maps_key));


        if (hotelName != null) {
            fetchHotelLocation(hotelName);
        }
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setZoomGesturesEnabled(true);
        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnInfoWindowClickListener(this);

        // Start geocoding + planning after map is ready
        startSmartPlanning();
    }

    // ==========================================
    // Core Logic: Planning & Weather
    // ==========================================

    private void fetchWeatherForDay(int day) {
        List<LatLonPoint> dayPoints = tripPlanManager.getDayPoints(day);
        if (dayPoints == null || dayPoints.isEmpty()) return;

        LatLonPoint location = dayPoints.get(0);
        String targetDateStr = getTargetDateStr(day);

        WeatherService.fetchWeather(this, location.getLatitude(), location.getLongitude(), targetDateStr,
                new WeatherService.WeatherCallback() {
                    @Override
                    public void onSuccess(String text, int iconResId) {
                        runOnUiThread(() -> {
                            binding.weatherText.setText(text);
                            binding.weatherIcon.setImageResource(iconResId);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> binding.weatherText.setText("Weather Unavailable"));
                    }
                });
    }

    // ==========================================
    // User Interaction: Add/Remove/Drag
    // ==========================================

    @Override
    public void onAttractionRemoved(int position) {
        List<String> currentDayNames = tripPlanManager.getDayNames(currentSelectedDay);
        if (currentDayNames == null || position >= currentDayNames.size()) return;

        String attractionToRemove = currentDayNames.get(position);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, attractionToRemove))
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> {
                    tripPlanManager.removeAttraction(currentSelectedDay, position);
                    displayPlanForDay(currentSelectedDay);
                })
                .setNegativeButton(R.string.dialog_delete_cancel, null)
                .show();
    }

    private void addPoiToCurrentDay(GoogleMapsService.PlaceItem place) {
        if (place == null) return;

        if (tripPlanManager.dayContains(currentSelectedDay, place.title)) {
            Toast.makeText(this,
                    getString(R.string.toast_already_in_trip, place.title),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        tripPlanManager.addPoiToDay(
                currentSelectedDay,
                place.title,
                new LatLonPoint(place.lat, place.lng)
        );

        Toast.makeText(this,
                getString(R.string.toast_added_to_day, place.title, currentSelectedDay),
                Toast.LENGTH_SHORT
        ).show();

        displayPlanForDay(currentSelectedDay);
    }

    @Override
    public void onAttractionsReordered() {
        List<String> newOrder = attractionAdapter.getAttractionNames();
        tripPlanManager.reorderDay(currentSelectedDay, newOrder);
        displayPlanForDay(currentSelectedDay);
    }

    // ==========================================
    // Search Logic (Google APIs only)
    // ==========================================

    private void startSmartPlanning() {
        Toast.makeText(this, getString(R.string.toast_locating_city), Toast.LENGTH_SHORT).show();
        mapsService.geocodeCity(city, (success, lat, lng) -> {
            runOnUiThread(() -> {
                if (success && googleMap != null) {
                    moveCameraToLocation(lat, lng);
                    poiSearchAllAttractions();
                } else {
                    Toast.makeText(this, "Locate Failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void poiSearchAllAttractions() {
        Log.d(TAG, "Starting POI search using Google Places...");
        hasRetried = false;
        this.attractionsToSearch = new ArrayList<>(this.originalAttractionNames);
        currentSearchIndex = 0;
        poiSearchNextAttraction();
    }

    private void poiSearchNextAttraction() {
        if (currentSearchIndex >= attractionsToSearch.size()) {
            runOnUiThread(this::handleSearchPassFinished);
            return;
        }

        String attractionName = attractionsToSearch.get(currentSearchIndex);

        mapsService.searchAttraction(attractionName, city, (success, point) -> {
            if (success && point != null) {
                tripPlanManager.addAttractionPoint(attractionName, point);
            }

            currentSearchIndex++;

            runOnUiThread(this::poiSearchNextAttraction);
        });
    }

    private void handleSearchPassFinished() {
        List<String> missingAttractions = tripPlanManager.getMissingAttractions();

        if (!missingAttractions.isEmpty() && !hasRetried) {
            hasRetried = true;
            this.attractionsToSearch = missingAttractions;
            this.currentSearchIndex = 0;
            Toast.makeText(this,
                    getString(R.string.toast_partial_search_failed),
                    Toast.LENGTH_SHORT
            ).show();
            poiSearchNextAttraction();
            return;
        }

        if (!tripPlanManager.hasAnyPoints()) {
            Toast.makeText(this,
                    getString(R.string.toast_all_parsing_failed),
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        tripPlanManager.distributeAttractionsToDays();
        setupDaySwitcherUI();
    }

    // ---- Google Places Nearby Search for "Search Nearby" ----

    private void searchNearbyRestaurants(double centerLat, double centerLng) {
        if (mapsService == null) return;

        mapsService.searchNearbyRestaurants(centerLat, centerLng, places -> {
            runOnUiThread(() -> {
                clearNearbyMarkers();

                if (places == null || places.isEmpty()) {
                    Toast.makeText(MapActivity.this,
                            "No restaurants found nearby.",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    for (GoogleMapsService.PlaceItem place : places) {
                        addNearbyMarker(place);
                    }
                    Toast.makeText(MapActivity.this,
                            "Nearby restaurants marked.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        });
    }



    // ==========================================
    // UI Display & Events
    // ========================================
    private void displayPlanForDay(int day) {
        this.currentSelectedDay = day;

        if (googleMap != null) {
            googleMap.clear();
        }

        markerPlaceItemMap.clear();
        nearbyPoiMarkers.clear();
        itineraryMarkerMap.clear();

        addOrUpdateHotelMarkerOnMap();

        fetchWeatherForDay(day);
        budgetController.updateBudgetUI(getTargetDateStr(day));

        List<LatLonPoint> dayPoints = tripPlanManager.getDayPoints(day);
        List<String> dayNames = tripPlanManager.getDayNames(day);

        attractionAdapter.updateData(dayNames);

        if (dayPoints == null || dayPoints.isEmpty()) return;

        // Draw POI markers
        for (int i = 0; i < dayPoints.size(); i++) {
            LatLonPoint p = dayPoints.get(i);
            LatLng latLng = new LatLng(p.getLatitude(), p.getLongitude());

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(dayNames.get(i))
                    .icon(getCustomMarker(String.valueOf(i + 1)));

            if (googleMap != null) {
                Marker marker = googleMap.addMarker(markerOptions);
                itineraryMarkerMap.put(marker, dayNames.get(i));
            }
        }

        if (hotelLatLng == null) return;  // IMPORTANT

        // -------- Build correct route chain --------
        List<LatLng> routeChain = new ArrayList<>();
        routeChain.add(hotelLatLng); // Start

        for (LatLonPoint p : dayPoints) {
            routeChain.add(new LatLng(p.getLatitude(), p.getLongitude()));
        }

        routeChain.add(hotelLatLng); // End back at hotel

        drawMultiSegmentRouteSafe(routeChain);

        // Center camera
        if (googleMap != null) {
            LatLonPoint first = dayPoints.get(0);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(first.getLatitude(), first.getLongitude()), 12));
        }
    }


    private void setupDaySwitcherUI() {
        binding.dayChipGroup.removeAllViews();
        String datePattern = getString(R.string.date_format);
        SimpleDateFormat chipSdf = new SimpleDateFormat(datePattern, Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(new SimpleDateFormat("yyyy-M-d", Locale.getDefault()).parse(startDateStr));
        } catch (Exception ignored) {}

        for (int i = 1; i <= totalDays; i++) {
            Chip chip = new Chip(this);
            String dateText = chipSdf.format(calendar.getTime());
            chip.setText(getString(R.string.day_label, i) + " (" + dateText + ")");
            chip.setCheckable(true);
            chip.setId(i);
            binding.dayChipGroup.addView(chip);
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        binding.dayChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) displayPlanForDay(checkedIds.get(0));
        });
        if (binding.dayChipGroup.getChildCount() > 0) {
            binding.dayChipGroup.check(binding.dayChipGroup.getChildAt(0).getId());
        }
    }

    // Detail Dialog & AI Review
    private void showPlaceDetailDialog(String placeName, String placeType, Marker marker, boolean isAlreadyInItinerary) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_place_detail, null);
        dialog.setContentView(view);
        ((TextView)view.findViewById(R.id.detailTitle)).setText(placeName);
        ((TextView)view.findViewById(R.id.detailType)).setText(placeType);

        View btnAdd = view.findViewById(R.id.btnAddFromDetail);
        if (isAlreadyInItinerary) btnAdd.setVisibility(View.GONE);
        else {
            btnAdd.setVisibility(View.VISIBLE);
            btnAdd.setOnClickListener(v -> {
                if (markerPlaceItemMap.containsKey(marker)) addPoiToCurrentDay(markerPlaceItemMap.get(marker));
                dialog.dismiss();
            });
        }
        dialog.show();
        fetchPlaceReviewFromAI(placeName,
                view.findViewById(R.id.detailRatingText),
                view.findViewById(R.id.detailRatingBar),
                view.findViewById(R.id.detailReview));
    }

    private void fetchPlaceReviewFromAI(String placeName,
                                        TextView ratingText,
                                        android.widget.RatingBar ratingBar,
                                        TextView reviewText) {

        executorService.execute(() -> {
            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "deepseek-chat");
                jsonBody.put("temperature", 0.7);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content",
                        "Provide JSON with 'rating' (float 3.0-5.0) and 'summary' (English, max 50 words) for: "
                                + placeName + " in " + city + ". No markdown."));
                jsonBody.put("messages", messages);
                jsonBody.put("response_format", new JSONObject().put("type", "json_object"));

                URL url = new URL("https://api.deepseek.com/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer sk-d51b987e1be546148868cc1fc988d52e");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);

                JSONObject outer = new JSONObject(response.toString());
                JSONObject message = outer.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message");
                JSONObject result = new JSONObject(message.getString("content"));

                runOnUiThread(() -> {
                    float rating = (float) result.optDouble("rating", 4.5);
                    ratingBar.setRating(rating);
                    ratingText.setText(String.valueOf(rating));
                    reviewText.setText(result.optString("summary", "Worth visiting."));
                });
            } catch (Exception e) {
                runOnUiThread(() -> reviewText.setText("Review unavailable."));
            }
        });
    }

    // ==========================================
    // Utils & boilerplate
    // ==========================================

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        if (markerPlaceItemMap.containsKey(marker)) {
            GoogleMapsService.PlaceItem place = markerPlaceItemMap.get(marker);
            showPlaceDetailDialog(place.title, place.type, marker, false);
            return true;
        }
        if (itineraryMarkerMap.containsKey(marker)) {
            showPlaceDetailDialog(itineraryMarkerMap.get(marker), "Attraction", marker, true);
            return true;
        }
        return false;
    }

    @Override
    public void onInfoWindowClick(@NonNull Marker marker) {
        // optional: open Google Maps, etc.
    }

    @Override
    public void onSearchNearbyClick(String attractionName) {
        LatLonPoint centerPoint = tripPlanManager.getPointForAttraction(attractionName);
        if (centerPoint == null) {
            Toast.makeText(this,
                    getString(R.string.toast_no_location_info),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        searchNearbyRestaurants(centerPoint.getLatitude(), centerPoint.getLongitude());
    }


    @Override
    public void onAttractionClick(int position) {
        List<LatLonPoint> currentPoints = tripPlanManager.getDayPoints(currentSelectedDay);

        if (currentPoints != null && position < currentPoints.size()) {

            LatLonPoint point = currentPoints.get(position);

            // Move camera
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(point.getLatitude(), point.getLongitude()), 15
                ));
            }

            // ---- ADD THIS: Request route from hotel to this attraction ----
            if (hotelLatLng != null) {

                LatLng poiLatLng = new LatLng(point.getLatitude(), point.getLongitude());

                routeManager.requestRoute(
                        hotelLatLng,
                        poiLatLng,
                        "walking", // or "driving" / "transit"
                        new RouteCallBack() {

                            @Override
                            public void onRouteReady(Polyline polyline) {
                                Toast.makeText(MapActivity.this, "Route Loaded", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onRouteError(String error) {
                                Toast.makeText(MapActivity.this, "Route Error: " + error, Toast.LENGTH_LONG).show();
                            }
                        }
                );
            }
        }
    }


    @Override
    public void requestDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
    }

    private void addNearbyMarker(GoogleMapsService.PlaceItem place) {
        if (googleMap == null) return;

        Marker marker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(place.lat, place.lng))
                .title(place.title)
                .icon(BitmapDescriptorFactory
                        .defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        nearbyPoiMarkers.add(marker);
        markerPlaceItemMap.put(marker, place);
    }

    private void clearNearbyMarkers() {
        for (Marker m : nearbyPoiMarkers) m.remove();
        nearbyPoiMarkers.clear();
        markerPlaceItemMap.clear();
    }

    private void setupRecyclerView() {
        attractionAdapter = new AttractionAdapter();
        attractionAdapter.setOnAttractionActionsListener(this);
        attractionAdapter.setStartDragListener(this);
        binding.attractionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.attractionsRecyclerView.setAdapter(attractionAdapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                attractionAdapter.onItemMove(vh.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {}

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                onAttractionsReordered();
            }
        });
        itemTouchHelper.attachToRecyclerView(binding.attractionsRecyclerView);
    }

    private void setupZoomButtons() {
        binding.btnZoomIn.setOnClickListener(v -> {
            if (googleMap != null) googleMap.animateCamera(CameraUpdateFactory.zoomIn());
        });
        binding.btnZoomOut.setOnClickListener(v -> {
            if (googleMap != null) googleMap.animateCamera(CameraUpdateFactory.zoomOut());
        });
    }

    private void moveCameraToLocation(double lat, double lng) {
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 11));
        }
    }

    private BitmapDescriptor getCustomMarker(String text) {
        View view = getLayoutInflater().inflate(R.layout.marker_layout, null);
        ((TextView)view.findViewById(R.id.marker_text)).setText(text);
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private String getTargetDateStr(int day) {
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(new SimpleDateFormat("yyyy-M-d", Locale.getDefault()).parse(startDateStr));
        } catch (Exception ignored) {}
        calendar.add(Calendar.DAY_OF_YEAR, day - 1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private void fetchHotelLocation(String hotelName) {
        if (hotelName == null || hotelName.isEmpty()) return;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // Use your Google API key
                String urlStr = "https://maps.googleapis.com/maps/api/geocode/json?address="
                        + Uri.encode(hotelName + " " + city)
                        + "&key=AIzaSyAR3DCQQ26plX8A7OUwAVp5lWWr_4hw1yE";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);

                JSONObject json = new JSONObject(sb.toString());

                if ("OK".equals(json.optString("status"))) {

                    JSONObject location = json.getJSONArray("results")
                            .getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location");

                    double lat = location.getDouble("lat");
                    double lng = location.getDouble("lng");
                    hotelLatLng = new LatLng(lat, lng);

                    runOnUiThread(() -> {
                        addOrUpdateHotelMarkerOnMap();
                        displayPlanForDay(currentSelectedDay); // <-- ADD THIS
                    });

                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    private void addOrUpdateHotelMarkerOnMap() {
        if (googleMap == null || hotelLatLng == null) return;

        // remove previous marker instance if exists (avoid duplicates)
        if (hotelMarker != null) {
            hotelMarker.remove();
            hotelMarker = null;
        }

        // Use a custom H marker if you want consistent look:
        BitmapDescriptor icon = getCustomMarker("H"); // or BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);

        hotelMarker = googleMap.addMarker(new MarkerOptions()
                .position(hotelLatLng)
                .title(hotelName != null ? hotelName : "Hotel")
                .snippet(hotelName != null ? hotelName : "")
                .icon(icon)
                .zIndex(999f) // draw on top
        );
    }

    private void drawMultiSegmentRouteSafe(List<LatLng> points) {
        if (routeManager == null || points.size() < 2) return;

        List<LatLng> finalPath = new ArrayList<>();

        new Thread(() -> {
            try {
                for (int i = 0; i < points.size() - 1; i++) {
                    List<LatLng> segment =
                            routeManager.syncRequestRoute(points.get(i), points.get(i + 1), "walking");

                    if (segment != null) {
                        finalPath.addAll(segment);
                    }
                }

                runOnUiThread(() -> {
                    if (currentPolyline != null) currentPolyline.remove();

                    currentPolyline = googleMap.addPolyline(new PolylineOptions()
                            .addAll(finalPath)
                            .width(15f)
                            .color(Color.rgb(0, 122, 255))   // blue
                            .clickable(false)
                    );
                });

            } catch (Exception ignored) { }
        }).start();
    }







    // Lifecycle
    @Override protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        executorService.shutdownNow();
    }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
    }
    @Override protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        mapView.onSaveInstanceState(out);
    }
}
