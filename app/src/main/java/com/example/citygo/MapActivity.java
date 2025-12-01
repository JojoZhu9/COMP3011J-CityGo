package com.example.citygo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.geocoder.GeocodeAddress;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkRouteResult;
import com.example.citygo.databinding.ActivityMapBinding;
import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements GeocodeSearch.OnGeocodeSearchListener, RouteSearch.OnRouteSearchListener, PoiSearch.OnPoiSearchListener, AttractionAdapter.OnAttractionActionsListener, AMap.OnMarkerClickListener, AMap.OnInfoWindowClickListener, AttractionAdapter.StartDragListener {

    private static final String TAG = "MapActivity";
    private ActivityMapBinding binding;
    private MapView mapView;
    private AMap aMap;

    // Services & API Helpers
    private GeocodeSearch geocodeSearch;
    private RouteSearch routeSearch;
    private PoiSearch poiSearch;
    private DBService dbService;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Data Models
    private String city;
    private int totalDays;
    private String startDateStr;
    private List<String> originalAttractionNames;
    private List<String> attractionsToSearch;

    // Core Logic Maps
    private Map<String, LatLonPoint> attractionPoints = new HashMap<>();
    private Map<Integer, List<LatLonPoint>> dailyPlans = new HashMap<>();
    private Map<Integer, List<String>> dailyPlanNames = new HashMap<>();

    // UI State
    private AttractionAdapter attractionAdapter;
    private int currentSearchIndex = 0;
    private boolean hasRetried = false;
    private int currentSelectedDay = 1;
    private List<Marker> nearbyPoiMarkers = new ArrayList<>();
    private Map<Marker, PoiItem> markerPoiItemMap = new HashMap<>();
    private Map<Marker, String> itineraryMarkerMap = new HashMap<>();
    private ItemTouchHelper itemTouchHelper;

    // Budget & Trip Info
    private int currentTripId = -1;
    private double dailyBudget = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐私合规检查
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. Init DB & Preferences
        dbService = new DBService(this);
        UserPrefs prefs = new UserPrefs(this);
        dailyBudget = prefs.getDailyBudgetCents() / 100.0;
        if (dailyBudget <= 0) dailyBudget = 500;

        // 2. Init Map
        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.getUiSettings().setZoomControlsEnabled(false);
            aMap.getUiSettings().setZoomGesturesEnabled(true);
            aMap.setOnMarkerClickListener(this);
        }

        setupRecyclerView();

        setupZoomButtons();

        // 3. Parse Intent
        Intent intent = getIntent();
        city = intent.getStringExtra("EXTRA_CITY");
        String attractionsStr = intent.getStringExtra("EXTRA_ATTRACTIONS");
        totalDays = intent.getIntExtra("EXTRA_DAYS", 0);
        startDateStr = intent.getStringExtra("EXTRA_START_DATE");

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

        // 5. Load Trip ID for Expenses
        dbService.getTrip(city, startDateStr, trip -> {
            if (trip != null) {
                currentTripId = trip.tripId;
                updateBudgetUI(1);
            }
        });

        // 6. UI Bindings
        binding.btnAddExpense.setOnClickListener(v -> showAddExpenseDialog());
        binding.btnBack.setOnClickListener(v -> finish());

        startSmartPlanning();
    }

    // ==========================================
    // Core Logic: Planning & Weather (Optimized)
    // ==========================================

    private void distributeAttractionsToDays() {
        // 1. 全局排序：利用 RouteOptimizer 工具类
        List<String> sortedAttractionNames = RouteOptimizer.sortAttractionsByNearestNeighbor(
                new ArrayList<>(attractionPoints.keySet()), attractionPoints);

        int attractionsPerDay = (int) Math.ceil((double) sortedAttractionNames.size() / totalDays);

        for (int i = 0; i < totalDays; i++) {
            List<String> dayNames = new ArrayList<>();
            int startIndex = i * attractionsPerDay;
            int endIndex = Math.min(startIndex + attractionsPerDay, sortedAttractionNames.size());

            if (startIndex < sortedAttractionNames.size()) {
                dayNames.addAll(sortedAttractionNames.subList(startIndex, endIndex));

                // 2. 局部优化：利用 RouteOptimizer 工具类
                List<String> optimalDayRouteNames = RouteOptimizer.findOptimalRoute(dayNames, attractionPoints);

                List<LatLonPoint> optimalDayPoints = new ArrayList<>();
                for (String name : optimalDayRouteNames) {
                    optimalDayPoints.add(attractionPoints.get(name));
                }

                dailyPlans.put(i + 1, optimalDayPoints);
                dailyPlanNames.put(i + 1, optimalDayRouteNames);
            } else {
                dailyPlans.put(i + 1, new ArrayList<>());
                dailyPlanNames.put(i + 1, new ArrayList<>());
            }
        }
    }

    private void fetchWeatherForDay(int day) {
        List<LatLonPoint> dayPoints = dailyPlans.get(day);
        if (dayPoints == null || dayPoints.isEmpty()) return;

        LatLonPoint location = dayPoints.get(0);
        String targetDateStr = getTargetDateStr(day);

        // 利用 WeatherService 工具类，移除大量冗余代码
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
        List<String> currentDayNames = dailyPlanNames.get(currentSelectedDay);
        if (currentDayNames == null || position >= currentDayNames.size()) return;

        String attractionToRemove = currentDayNames.get(position);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, attractionToRemove))
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> {
                    currentDayNames.remove(position);
                    dailyPlans.get(currentSelectedDay).remove(position);

                    // Re-optimize route using Tool class
                    List<String> optimalDayRouteNames = RouteOptimizer.findOptimalRoute(currentDayNames, attractionPoints);
                    refreshDailyPlanModel(currentSelectedDay, optimalDayRouteNames);
                    displayPlanForDay(currentSelectedDay);
                })
                .setNegativeButton(R.string.dialog_delete_cancel, null)
                .show();
    }

    private void addPoiToCurrentDay(PoiItem poi) {
        if (poi == null) return;
        List<String> currentDayNames = dailyPlanNames.get(currentSelectedDay);
        if (currentDayNames == null) currentDayNames = new ArrayList<>();

        if (currentDayNames.contains(poi.getTitle())) {
            Toast.makeText(this, getString(R.string.toast_already_in_trip, poi.getTitle()), Toast.LENGTH_SHORT).show();
            return;
        }

        currentDayNames.add(poi.getTitle());
        attractionPoints.put(poi.getTitle(), poi.getLatLonPoint());

        // Re-optimize route using Tool class
        List<String> optimalDayRouteNames = RouteOptimizer.findOptimalRoute(currentDayNames, attractionPoints);
        refreshDailyPlanModel(currentSelectedDay, optimalDayRouteNames);

        Toast.makeText(this, getString(R.string.toast_added_to_day, poi.getTitle(), currentSelectedDay), Toast.LENGTH_SHORT).show();
        displayPlanForDay(currentSelectedDay);
    }

    @Override
    public void onAttractionsReordered() {
        List<String> newOrder = attractionAdapter.getAttractionNames();
        refreshDailyPlanModel(currentSelectedDay, newOrder);
        // Dragging implies user intent for order, so we DON'T re-optimize with TSP here
        displayPlanForDay(currentSelectedDay);
    }

    private void refreshDailyPlanModel(int day, List<String> names) {
        dailyPlanNames.put(day, new ArrayList<>(names));
        List<LatLonPoint> points = new ArrayList<>();
        for (String name : names) {
            points.add(attractionPoints.get(name));
        }
        dailyPlans.put(day, points);
    }

    // ==========================================
    // Search Logic (AMap + Google Fallback)
    // ==========================================

    private void startSmartPlanning() {
        Toast.makeText(this, getString(R.string.toast_locating_city), Toast.LENGTH_SHORT).show();
        try {
            geocodeSearch = new GeocodeSearch(this);
            geocodeSearch.setOnGeocodeSearchListener(this);
            routeSearch = new RouteSearch(this);
            routeSearch.setRouteSearchListener(this);
            poiSearch = new PoiSearch(this, null);
            poiSearch.setOnPoiSearchListener(this);

            Log.d(TAG, "Starting with AMap priority...");
            GeocodeQuery query = new GeocodeQuery(city, city);
            geocodeSearch.getFromLocationNameAsyn(query);
        } catch (AMapException e) {
            e.printStackTrace();
        }
    }

    // AMap Geocode Callback
    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
        if (rCode == AMapException.CODE_AMAP_SUCCESS && geocodeResult != null && !geocodeResult.getGeocodeAddressList().isEmpty()) {
            GeocodeAddress address = geocodeResult.getGeocodeAddressList().get(0);
            LatLonPoint point = address.getLatLonPoint();
            moveCameraToLocation(point.getLatitude(), point.getLongitude());
            poiSearchAllAttractions();
        } else {
            geocodeCityWithGoogle(city); // Fallback
        }
    }

    private void poiSearchAllAttractions() {
        Log.d(TAG, "Step 2: Starting POI search...");
        hasRetried = false;
        this.attractionsToSearch = new ArrayList<>(this.originalAttractionNames);
        currentSearchIndex = 0;
        poiSearchNextAttraction();
    }

    private void poiSearchNextAttraction() {
        if (currentSearchIndex >= attractionsToSearch.size()) {
            handleSearchPassFinished();
            return;
        }
        String attractionName = attractionsToSearch.get(currentSearchIndex);

        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            try {
                // 第三个参数 city 必须是有效的城市名称或 CityCode
                PoiSearch.Query query = new PoiSearch.Query(attractionName, "", city);

                // 【核心修复】开启严格城市限制，强制只在当前城市内搜索
                query.setCityLimit(true);

                query.setPageSize(1);
                poiSearch.setQuery(query);
                poiSearch.searchPOIAsyn();
            } catch (Exception e) { e.printStackTrace(); }
        }, 400);
    }

    // AMap POI Callback
    @Override
    public void onPoiSearched(PoiResult poiResult, int rCode) {
        // Case 1: Nearby Search Results
        if (poiSearch.getBound() != null) {
            clearNearbyMarkers();
            if (rCode == AMapException.CODE_AMAP_SUCCESS && poiResult != null && !poiResult.getPois().isEmpty()) {
                for (PoiItem poi : poiResult.getPois()) {
                    addNearbyMarker(poi);
                }
                Toast.makeText(this, getString(R.string.toast_marked_recommendations), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.toast_no_nearby_places), Toast.LENGTH_SHORT).show();
            }
            poiSearch.setBound(null); // Reset bound
        }
        // Case 2: Attraction Parsing
        else {
            String currentAttractionName = attractionsToSearch.get(currentSearchIndex);
            if (rCode == AMapException.CODE_AMAP_SUCCESS && poiResult != null && !poiResult.getPois().isEmpty()) {
                PoiItem poiItem = poiResult.getPois().get(0);
                attractionPoints.put(currentAttractionName, poiItem.getLatLonPoint());
                currentSearchIndex++;
                poiSearchNextAttraction();
            } else {
                // AMap failed, try Google
                searchAttractionWithGoogle(currentAttractionName);
            }
        }
    }

    private void handleSearchPassFinished() {
        List<String> missingAttractions = new ArrayList<>();
        for (String name : originalAttractionNames) {
            if (!attractionPoints.containsKey(name)) missingAttractions.add(name);
        }

        if (!missingAttractions.isEmpty() && !hasRetried) {
            hasRetried = true;
            this.attractionsToSearch = missingAttractions;
            this.currentSearchIndex = 0;
            Toast.makeText(this, getString(R.string.toast_partial_search_failed), Toast.LENGTH_SHORT).show();
            poiSearchNextAttraction();
            return;
        }

        if (attractionPoints.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_all_parsing_failed), Toast.LENGTH_LONG).show();
            return;
        }

        distributeAttractionsToDays();
        setupDaySwitcherUI();
    }

    // Google API Helpers
    private void geocodeCityWithGoogle(String cityName) {
        String urlStr = "https://maps.googleapis.com/maps/api/geocode/json?address=" + Uri.encode(cityName) + "&key=AIzaSyAiEbD5dYtwZETZg9MLAmKg1EVrcJzA3PA";
        runGoogleApiRequest(urlStr, json -> {
            if ("OK".equals(json.getString("status"))) {
                JSONObject location = json.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
                double lat = location.getDouble("lat");
                double lng = location.getDouble("lng");
                runOnUiThread(() -> moveCameraToLocation(lat, lng));
                runOnUiThread(this::poiSearchAllAttractions);
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Locate Failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void searchAttractionWithGoogle(String attractionName) {
        // Strict search using "Name in City" format
        String query = attractionName + " in " + city;
        String urlStr = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=" + Uri.encode(query) + "&key=AIzaSyAiEbD5dYtwZETZg9MLAmKg1EVrcJzA3PA";

        runGoogleApiRequest(urlStr, json -> {
            if ("OK".equals(json.getString("status"))) {
                JSONObject loc = json.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
                LatLonPoint point = new LatLonPoint(loc.getDouble("lat"), loc.getDouble("lng"));
                attractionPoints.put(attractionName, point);
            }
        });

        // Always proceed to next, regardless of success
        runOnUiThread(() -> {
            currentSearchIndex++;
            poiSearchNextAttraction();
        });
    }

    // Helper to reduce boilerplate HTTP code
    interface JsonCallback { void onResult(JSONObject json) throws Exception; }
    private void runGoogleApiRequest(String urlStr, JsonCallback callback) {
        executorService.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                JSONObject json = new JSONObject(sb.toString());
                callback.onResult(json);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    // ==========================================
    // UI Display & Events
    // ==========================================

    private void displayPlanForDay(int day) {
        this.currentSelectedDay = day;
        aMap.clear();
        markerPoiItemMap.clear();
        nearbyPoiMarkers.clear();
        itineraryMarkerMap.clear();

        fetchWeatherForDay(day);
        updateBudgetUI(day);

        List<LatLonPoint> dayPoints = dailyPlans.get(day);
        List<String> dayNames = dailyPlanNames.get(day);

        attractionAdapter.updateData(dayNames);
        if (dayPoints == null || dayPoints.isEmpty()) return;

        for (int i = 0; i < dayPoints.size(); i++) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(new LatLng(dayPoints.get(i).getLatitude(), dayPoints.get(i).getLongitude()))
                    .title(dayNames.get(i))
                    .icon(getCustomMarker(String.valueOf(i + 1)));
            Marker marker = aMap.addMarker(markerOptions);
            itineraryMarkerMap.put(marker, dayNames.get(i));
        }

        if (dayPoints.size() >= 2) {
            LatLonPoint from = dayPoints.get(0);
            LatLonPoint to = dayPoints.get(dayPoints.size() - 1);
            List<LatLonPoint> passby = (dayPoints.size() > 2) ? dayPoints.subList(1, dayPoints.size() - 1) : new ArrayList<>();
            routeSearch.calculateDriveRouteAsyn(new RouteSearch.DriveRouteQuery(new RouteSearch.FromAndTo(from, to), RouteSearch.DRIVING_SINGLE_DEFAULT, passby, null, ""));
        }

        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(dayPoints.get(0).getLatitude(), dayPoints.get(0).getLongitude()), 12));
    }

    private void setupDaySwitcherUI() {
        binding.dayChipGroup.removeAllViews();
        String datePattern = getString(R.string.date_format);
        SimpleDateFormat chipSdf = new SimpleDateFormat(datePattern, Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        try { calendar.setTime(new SimpleDateFormat("yyyy-M-d", Locale.getDefault()).parse(startDateStr)); } catch (Exception e) {}

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

    // Expense & Budget Logic
    private void updateBudgetUI(int day) {
        if (currentTripId == -1) return;
        String currentDateStr = getTargetDateStr(day);
        binding.progressBarBudget.setOnClickListener(v -> showExpenseListDialog(currentDateStr));
        dbService.getDailyTotal(currentTripId, currentDateStr, total -> runOnUiThread(() -> {
            binding.textExpense.setText(String.format(Locale.US, "%.2f / %.0f", total, dailyBudget));
            binding.progressBarBudget.setProgress(Math.min((int)((total/dailyBudget)*100), 100));
            binding.progressBarBudget.getProgressDrawable().setColorFilter(
                    total > dailyBudget ? Color.RED : 0, android.graphics.PorterDuff.Mode.SRC_IN);
            if(total <= dailyBudget) binding.progressBarBudget.getProgressDrawable().clearColorFilter();
        }));
    }

    private void showExpenseListDialog(String dateStr) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Expenses for " + dateStr);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        ExpenseAdapter adapter = new ExpenseAdapter();
        rv.setAdapter(adapter);
        rv.setPadding(32, 32, 32, 32);
        builder.setView(rv);
        builder.setPositiveButton("Close", null);
        builder.show();

        dbService.getDailyExpensesList(currentTripId, dateStr, list -> {
            if (list.isEmpty()) Toast.makeText(this, "No expenses yet.", Toast.LENGTH_SHORT).show();
            else adapter.setExpenses(list);
        });
        adapter.setOnExpenseDeleteListener(expense -> {
            dbService.deleteExpense(expense);
            dbService.getDailyExpensesList(currentTripId, dateStr, adapter::setExpenses);
            updateBudgetUI(currentSelectedDay);
        });
    }

    private void showAddExpenseDialog() {
        if (currentTripId == -1) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);
        builder.setView(view);
        com.google.android.material.textfield.TextInputEditText inputAmount = view.findViewById(R.id.inputAmount);
        com.google.android.material.chip.ChipGroup chipGroup = view.findViewById(R.id.chipGroupCategory);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String amountStr = inputAmount.getText().toString();
            if (amountStr.isEmpty()) return;
            String category = "Other";
            if (chipGroup.getCheckedChipId() != -1) {
                category = ((Chip)view.findViewById(chipGroup.getCheckedChipId())).getText().toString();
            }
            dbService.addExpense(currentTripId, category, Double.parseDouble(amountStr), getTargetDateStr(currentSelectedDay));
            updateBudgetUI(currentSelectedDay);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Detail Dialog & DeepSeek Review
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
                if (markerPoiItemMap.containsKey(marker)) addPoiToCurrentDay(markerPoiItemMap.get(marker));
                dialog.dismiss();
            });
        }
        dialog.show();
        fetchPlaceReviewFromAI(placeName, view.findViewById(R.id.detailRatingText), view.findViewById(R.id.detailRatingBar), view.findViewById(R.id.detailReview));
    }

    private void fetchPlaceReviewFromAI(String placeName, TextView ratingText, android.widget.RatingBar ratingBar, TextView reviewText) {
        executorService.execute(() -> {
            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "deepseek-chat");
                jsonBody.put("temperature", 0.7);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content",
                        "Provide JSON with 'rating' (float 3.0-5.0) and 'summary' (English, max 50 words) for: " + placeName + " in " + city + ". No markdown."));
                jsonBody.put("messages", messages);
                jsonBody.put("response_format", new JSONObject().put("type", "json_object"));

                URL url = new URL("https://api.deepseek.com/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer sk-d51b987e1be546148868cc1fc988d52e");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(jsonBody.toString().getBytes("UTF-8"));

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);

                JSONObject result = new JSONObject(new JSONObject(response.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
                runOnUiThread(() -> {
                    ratingBar.setRating((float) result.optDouble("rating", 4.5));
                    ratingText.setText(String.valueOf(result.optDouble("rating", 4.5)));
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
    public boolean onMarkerClick(Marker marker) {
        if (markerPoiItemMap.containsKey(marker)) {
            PoiItem poi = markerPoiItemMap.get(marker);
            showPlaceDetailDialog(poi.getTitle(), poi.getTypeDes(), marker, false);
            return true;
        }
        if (itineraryMarkerMap.containsKey(marker)) {
            showPlaceDetailDialog(itineraryMarkerMap.get(marker), "Attraction", marker, true);
            return true;
        }
        return false;
    }

    @Override
    public void onSearchNearbyClick(String attractionName) {
        LatLonPoint centerPoint = attractionPoints.get(attractionName);
        if (centerPoint == null) {
            Toast.makeText(this, getString(R.string.toast_no_location_info), Toast.LENGTH_SHORT).show();
            return;
        }
        PoiSearch.Query query = new PoiSearch.Query(getString(R.string.poi_search_query), "050000", city);
        query.setPageSize(10);
        poiSearch.setBound(new PoiSearch.SearchBound(centerPoint, 500));
        poiSearch.setQuery(query);
        poiSearch.searchPOIAsyn();
    }

    @Override
    public void onAttractionClick(int position) {
        // 1. 获取当前展示的天数对应的坐标列表
        List<LatLonPoint> currentPoints = dailyPlans.get(currentSelectedDay);

        if (currentPoints != null && position < currentPoints.size()) {
            LatLonPoint point = currentPoints.get(position);

            // 2. 移动地图中心到该点，并放大
            if (aMap != null) {
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(point.getLatitude(), point.getLongitude()),
                        15 // 放大级别 15 比较适合查看具体地点
                ));

                // 可选：显示个 Toast 提示用户
                String name = dailyPlanNames.get(currentSelectedDay).get(position);
                // Toast.makeText(this, "Located: " + name, Toast.LENGTH_SHORT).show();

                // 可选：如果对应的 Marker 存在，可以显示 InfoWindow
                // 需要遍历 itineraryMarkerMap 找到对应的 Marker 并 marker.showInfoWindow();
            }
        }
    }

    @Override
    public void requestDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult result, int errorCode) {
        if (errorCode == AMapException.CODE_AMAP_SUCCESS && result != null && !result.getPaths().isEmpty()) {
            DrivePath drivePath = result.getPaths().get(0);
            List<LatLng> routePath = new ArrayList<>();
            drivePath.getSteps().forEach(step -> step.getPolyline().forEach(point -> routePath.add(new LatLng(point.getLatitude(), point.getLongitude()))));
            aMap.addPolyline(new PolylineOptions().addAll(routePath).width(20f).color(Color.WHITE));
            aMap.addPolyline(new PolylineOptions().addAll(routePath).width(16f).color(Color.argb(255, 1, 159, 241)));
        }
    }

    private void addNearbyMarker(PoiItem poi) {
        Marker marker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(poi.getLatLonPoint().getLatitude(), poi.getLatLonPoint().getLongitude()))
                .title(poi.getTitle())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        nearbyPoiMarkers.add(marker);
        markerPoiItemMap.put(marker, poi);
    }

    private void clearNearbyMarkers() {
        for(Marker m : nearbyPoiMarkers) m.remove();
        nearbyPoiMarkers.clear();
        markerPoiItemMap.clear();
    }

    private void setupRecyclerView() {
        attractionAdapter = new AttractionAdapter();
        attractionAdapter.setOnAttractionActionsListener(this);
        attractionAdapter.setStartDragListener(this);
        binding.attractionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.attractionsRecyclerView.setAdapter(attractionAdapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                attractionAdapter.onItemMove(vh.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int d) {}
            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                onAttractionsReordered();
            }
        });
        itemTouchHelper.attachToRecyclerView(binding.attractionsRecyclerView);
    }

    private void setupZoomButtons() {
        // 放大
        binding.btnZoomIn.setOnClickListener(v -> {
            if (aMap != null) {
                // 使用 animateCamera 会有平滑的动画效果
                aMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        // 缩小
        binding.btnZoomOut.setOnClickListener(v -> {
            if (aMap != null) {
                aMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });
    }

    private void moveCameraToLocation(double lat, double lng) {
        if (aMap != null) aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 11));
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
        try { calendar.setTime(new SimpleDateFormat("yyyy-M-d", Locale.getDefault()).parse(startDateStr)); } catch (Exception e) {}
        calendar.add(Calendar.DAY_OF_YEAR, day - 1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    // Required Interface Stubs
    @Override public void onInfoWindowClick(Marker marker) {}
    @Override public void onPoiItemSearched(PoiItem p, int i) {}
    @Override public void onRegeocodeSearched(RegeocodeResult r, int i) {}
    @Override public void onBusRouteSearched(BusRouteResult b, int i) {}
    @Override public void onWalkRouteSearched(WalkRouteResult w, int i) {}
    @Override public void onRideRouteSearched(RideRouteResult r, int i) {}
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out); }
}