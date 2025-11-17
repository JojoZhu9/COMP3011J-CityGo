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
import com.amap.api.maps.AMapUtils;
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
import com.google.android.material.chip.Chip;
import com.example.citygo.databinding.ActivityMapBinding;

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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements GeocodeSearch.OnGeocodeSearchListener, RouteSearch.OnRouteSearchListener, PoiSearch.OnPoiSearchListener, AttractionAdapter.OnAttractionActionsListener, AMap.OnMarkerClickListener, AMap.OnInfoWindowClickListener, AttractionAdapter.StartDragListener {
    private static final String TAG = "SmartPlanDebug";
    private ActivityMapBinding binding;
    private MapView mapView;
    private AMap aMap;
    private GeocodeSearch geocodeSearch;
    private RouteSearch routeSearch;
    private PoiSearch poiSearch;
    private AttractionAdapter attractionAdapter;
    private String city;
    private List<String> originalAttractionNames;
    private List<String> attractionsToSearch;
    private int totalDays;
    private String startDateStr;
    private Map<String, LatLonPoint> attractionPoints = new HashMap<>();
    private Map<Integer, List<LatLonPoint>> dailyPlans = new HashMap<>();
    private Map<Integer, List<String>> dailyPlanNames = new HashMap<>();
    private int currentSearchIndex = 0;
    private boolean hasRetried = false;

    // **New: For storing and managing markers of recommended points**
    private List<Marker> nearbyPoiMarkers = new ArrayList<>();
    // **New: Associate Markers with their corresponding POI data**
    private Map<Marker, PoiItem> markerPoiItemMap = new HashMap<>();
    // **New: Record the currently selected day**
    private int currentSelectedDay = 1;
    private ItemTouchHelper itemTouchHelper;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DBService dbService;
    private int currentTripId = -1;
    private double dailyBudget = 0;
    private Map<Marker, String> itineraryMarkerMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. 初始化数据库服务和预算
        dbService = new DBService(this);
        UserPrefs prefs = new UserPrefs(this);
        dailyBudget = prefs.getDailyBudgetCents() / 100.0;
        if (dailyBudget <= 0) dailyBudget = 500; // 默认预算

        // 2. 初始化地图
        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.getUiSettings().setZoomControlsEnabled(true);
            aMap.setOnMarkerClickListener(this);
            aMap.setOnInfoWindowClickListener(this);
        }

        setupRecyclerView();

        // 3. 获取 Intent 数据
        Intent intent = getIntent();
        city = intent.getStringExtra("EXTRA_CITY");
        String attractionsStr = intent.getStringExtra("EXTRA_ATTRACTIONS");
        totalDays = intent.getIntExtra("EXTRA_DAYS", 0);
        startDateStr = intent.getStringExtra("EXTRA_START_DATE");

        originalAttractionNames = new ArrayList<>();
        if (attractionsStr != null) {
            for (String name : attractionsStr.split("[,，]")) {
                if (!name.trim().isEmpty()) {
                    originalAttractionNames.add(name.trim());
                }
            }
        }

        Log.d(TAG, "Loaded attractions: " + originalAttractionNames);

        // 4. 数据校验
        if (totalDays == 0 || originalAttractionNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_incomplete_trip), Toast.LENGTH_LONG).show();
            return;
        }

        // ============================================================
        // 【新增整合部分开始】
        // ============================================================

        // 5. 异步获取行程ID (必须在获取到 city 和 startDateStr 之后调用)
        // 只有拿到 tripId，才能进行记账操作
        dbService.getTrip(city, startDateStr, trip -> {
            if (trip != null) {
                currentTripId = trip.tripId;
                // 获取到ID后，刷新第一天的预算显示
                updateBudgetUI(1);
            }
        });

        // 6. 绑定“记账”按钮点击事件
        binding.btnAddExpense.setOnClickListener(v -> showAddExpenseDialog());

        // 7. 绑定“返回”按钮点击事件
        binding.btnBack.setOnClickListener(v -> finish());

        // ============================================================
        // 【新增整合部分结束】
        // ============================================================

        startSmartPlanning();
    }
    // 替换原有的 fetchWeatherForDay 方法
    private void fetchWeatherForDay(int day) {
        List<LatLonPoint> dayPoints = dailyPlans.get(day);
        if (dayPoints == null || dayPoints.isEmpty()) {
            binding.weatherText.setText(getString(R.string.weather_no_info)); // 确保 strings.xml 中有这个字符串，或者直接写 "无行程信息"
            binding.weatherIcon.setImageResource(0);
            return;
        }

        LatLonPoint location = dayPoints.get(0);
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        // 计算目标日期 (Open-Meteo 需要 yyyy-MM-dd 格式)
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdfInput = new SimpleDateFormat("yyyy-M-d", Locale.getDefault()); // 解析 CreateTrip 传来的格式
        SimpleDateFormat sdfApi = new SimpleDateFormat("yyyy-MM-dd", Locale.US); // API 需要的格式

        String targetDateStr = "";
        try {
            Date startDate = sdfInput.parse(startDateStr);
            calendar.setTime(startDate);
            calendar.add(Calendar.DAY_OF_YEAR, day - 1);
            targetDateStr = sdfApi.format(calendar.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
            // 如果解析失败，默认用今天
            targetDateStr = sdfApi.format(new Date());
        }

        final String apiDate = targetDateStr;

        executorService.execute(() -> {
            try {
                // Open-Meteo API: 获取指定日期的 天气代码、最高温、最低温
                // timezone=auto 会自动根据经纬度判断时区
                String urlStr = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=auto&start_date=%s&end_date=%s",
                        lat, lon, apiDate, apiDate);

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.connect();

                InputStream stream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }
                String jsonResponse = buffer.toString();
                Log.d(TAG, "Open-Meteo Response: " + jsonResponse);

                JSONObject jsonObject = new JSONObject(jsonResponse);

                // Open-Meteo 的 daily 数据是数组，因为我们只查了一天，所以取 index 0
                JSONObject daily = jsonObject.getJSONObject("daily");
                int code = daily.getJSONArray("weathercode").getInt(0);
                double maxTemp = daily.getJSONArray("temperature_2m_max").getDouble(0);
                double minTemp = daily.getJSONArray("temperature_2m_min").getDouble(0);

                // 获取对应的图标资源ID和文字描述
                int iconResId = getWeatherIconResource(code);
                String weatherDesc = getWeatherDescription(code);

                final String weatherInfo = String.format(Locale.getDefault(), "%s  %.0f°C / %.0f°C", weatherDesc, minTemp, maxTemp);

                runOnUiThread(() -> {
                    binding.weatherText.setText(weatherInfo);
                    if (iconResId != 0) {
                        binding.weatherIcon.setImageResource(iconResId);
                    } else {
                        binding.weatherIcon.setImageResource(0);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Weather fetch error", e);
                runOnUiThread(() -> binding.weatherText.setText("天气暂不可用"));
            }
        });
    }

    /**
     * 将 WMO Weather Code 转换为和风天气的图标资源 ID
     * 代码参考: https://open-meteo.com/en/docs
     */
    private int getWeatherIconResource(int wmoCode) {
        String iconName;

        if (wmoCode == 0) {
            iconName = "ic_qweather_100"; // 晴
        } else if (wmoCode >= 1 && wmoCode <= 3) {
            iconName = "ic_qweather_101"; // 多云
        } else if (wmoCode == 45 || wmoCode == 48) {
            iconName = "ic_qweather_501"; // 雾 (如果没有501，可以用104阴)
        } else if (wmoCode >= 51 && wmoCode <= 67) {
            iconName = "ic_qweather_305"; // 雨
        } else if (wmoCode >= 71 && wmoCode <= 77) {
            iconName = "ic_qweather_400"; // 雪
        } else if (wmoCode >= 80 && wmoCode <= 82) {
            iconName = "ic_qweather_305"; // 阵雨
        } else if (wmoCode >= 95) {
            iconName = "ic_qweather_302"; // 雷雨
        } else {
            iconName = "ic_qweather_100"; // 默认
        }

        // 动态获取资源ID，避免硬编码 R.drawable.xxx 导致找不到报错
        return getResources().getIdentifier(iconName, "drawable", getPackageName());
    }

    /**
     * 获取天气文字描述
     */
    /**
     * Get weather description based on WMO Weather Code (English)
     */
    private String getWeatherDescription(int wmoCode) {
        if (wmoCode == 0) return "Clear sky";
        if (wmoCode >= 1 && wmoCode <= 3) return "Cloudy";
        if (wmoCode == 45 || wmoCode == 48) return "Fog";
        if (wmoCode >= 51 && wmoCode <= 67) return "Rain";
        if (wmoCode >= 71 && wmoCode <= 77) return "Snow";
        if (wmoCode >= 80 && wmoCode <= 82) return "Showers";
        if (wmoCode >= 95) return "Thunderstorm";
        return "Unknown";
    }

    // Implement the listener method for removing attractions
    @Override
    public void onAttractionRemoved(int position) {
        List<String> currentDayNames = dailyPlanNames.get(currentSelectedDay);
        if (currentDayNames == null || position >= currentDayNames.size()) return;

        String attractionToRemove = currentDayNames.get(position);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, attractionToRemove))
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> {
                    // Remove from the data model
                    currentDayNames.remove(position);
                    dailyPlans.get(currentSelectedDay).remove(position);

                    // Re-plan the optimal route for the day
                    List<String> optimalDayRouteNames = findOptimalRoute(currentDayNames);
                    List<LatLonPoint> optimalDayPoints = new ArrayList<>();
                    for (String name : optimalDayRouteNames) {
                        optimalDayPoints.add(attractionPoints.get(name));
                    }
                    dailyPlanNames.put(currentSelectedDay, optimalDayRouteNames);
                    dailyPlans.put(currentSelectedDay, optimalDayPoints);

                    // Refresh the map and list
                    displayPlanForDay(currentSelectedDay);
                })
                .setNegativeButton(R.string.dialog_delete_cancel, null)
                .show();
    }

    // Implement the listener method for drag completion
    @Override
    public void onAttractionsReordered() {
        // After dragging ends, the list order in the Adapter is already new
        List<String> newOrder = attractionAdapter.getAttractionNames();

        // Update our data model
        dailyPlanNames.put(currentSelectedDay, new ArrayList<>(newOrder));
        List<LatLonPoint> newPointsOrder = new ArrayList<>();
        for (String name : newOrder) {
            newPointsOrder.add(attractionPoints.get(name));
        }
        dailyPlans.put(currentSelectedDay, newPointsOrder);

        // **Directly re-plan the route with the new order and refresh, no need to run TSP**
        displayPlanForDay(currentSelectedDay);
    }

    // Implement the listener method for starting a drag
    @Override
    public void requestDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

    // 1. 修改 onMarkerClick：点击标记不再显示气泡，而是显示详情弹窗
    @Override
    public boolean onMarkerClick(Marker marker) {
        // 情况 1: 点击了“搜周边”的推荐点（绿色）
        if (markerPoiItemMap.containsKey(marker)) {
            PoiItem poi = markerPoiItemMap.get(marker);
            // 最后一个参数 false 表示“它还不在行程中”
            showPlaceDetailDialog(poi.getTitle(), poi.getTypeDes(), marker, false);
            return true;
        }

        // 情况 2: 【新增】点击了“已有行程”的景点（蓝色数字）
        if (itineraryMarkerMap.containsKey(marker)) {
            String attractionName = itineraryMarkerMap.get(marker);
            // 最后一个参数 true 表示“它已经在行程中了”（为了隐藏添加按钮）
            showPlaceDetailDialog(attractionName, "Attraction", marker, true);
            return true;
        }

        return false;
    }

    // 2. 我们可以移除 onInfoWindowClick 了，因为不再使用它
    @Override
    public void onInfoWindowClick(Marker marker) {
        // Deprecated
    }

    // 3. 【新增】显示详情弹窗的核心方法
    // 【修改】增加 boolean isAlreadyInItinerary 参数
    private void showPlaceDetailDialog(String placeName, String placeType, Marker marker, boolean isAlreadyInItinerary) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        View view = getLayoutInflater().inflate(R.layout.dialog_place_detail, null);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.detailTitle);
        TextView type = view.findViewById(R.id.detailType);
        TextView ratingText = view.findViewById(R.id.detailRatingText);
        TextView reviewText = view.findViewById(R.id.detailReview);
        android.widget.RatingBar ratingBar = view.findViewById(R.id.detailRatingBar);
        View btnAdd = view.findViewById(R.id.btnAddFromDetail);

        title.setText(placeName);
        type.setText(placeType);

        // 【新增】根据是否已在行程中，控制按钮显示
        if (isAlreadyInItinerary) {
            btnAdd.setVisibility(View.GONE); // 已经在行程里了，隐藏按钮
        } else {
            btnAdd.setVisibility(View.VISIBLE);
            btnAdd.setOnClickListener(v -> {
                if (markerPoiItemMap.containsKey(marker)) {
                    PoiItem poiToAdd = markerPoiItemMap.get(marker);
                    addPoiToCurrentDay(poiToAdd);
                }
                dialog.dismiss();
            });
        }

        dialog.show();

        // 调用 AI 获取评价 (确保这部分代码里的 Prompt 是英文的)
        fetchPlaceReviewFromAI(placeName, ratingText, ratingBar, reviewText);
    }

    // 5. 【新增】调用 DeepSeek AI 获取评价
    private void fetchPlaceReviewFromAI(String placeName, TextView ratingText, android.widget.RatingBar ratingBar, TextView reviewText) {
        String apiKey = "sk-d51b987e1be546148868cc1fc988d52e";
        executorService.execute(() -> {
            try {
                // 构造 Prompt：要求返回 JSON，包含 rating (0.0-5.0) 和 summary
                String prompt = "Provide a JSON object with 'rating' (float 3.0-5.0) and 'summary' (English, max 50 words) for the place: " + placeName + " in " + city + ". Based on general public opinion. Do not use markdown.";

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "deepseek-chat");
                jsonBody.put("temperature", 0.7);

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", prompt));
                jsonBody.put("messages", messages);
                jsonBody.put("response_format", new JSONObject().put("type", "json_object"));

                URL url = new URL("https://api.deepseek.com/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey); // 使用真实的 Key
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);

                    JSONObject responseJson = new JSONObject(response.toString());
                    String content = responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

                    // 简单的 JSON 清理
                    if(content.contains("```json")) content = content.replace("```json", "").replace("```", "");

                    JSONObject result = new JSONObject(content);
                    double rating = result.optDouble("rating", 4.5);
                    String summary = result.optString("summary", "A popular place worth visiting.");

                    // 更新 UI
                    runOnUiThread(() -> {
                        ratingBar.setRating((float) rating);
                        ratingText.setText(String.valueOf(rating));
                        reviewText.setText(summary);
                    });
                } else {
                    runOnUiThread(() -> reviewText.setText("Review data unavailable."));
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> reviewText.setText("Failed to load reviews."));
            }
        });
    }

    // **New: Core logic to add a POI to the current day's itinerary**
    private void addPoiToCurrentDay(PoiItem poi) {
        if (poi == null) return;

        // Get the current day's itinerary list
        List<String> currentDayNames = dailyPlanNames.get(currentSelectedDay);
        if (currentDayNames == null) {
            currentDayNames = new ArrayList<>();
        }

        // Check if it's already added
        if (currentDayNames.contains(poi.getTitle())) {
            Toast.makeText(this, getString(R.string.toast_already_in_trip, poi.getTitle()), Toast.LENGTH_SHORT).show();
            return;
        }

        // Add the new location
        currentDayNames.add(poi.getTitle());
        attractionPoints.put(poi.getTitle(), poi.getLatLonPoint());

        // **Key step: Recalculate the optimal route for the day's itinerary**
        List<String> optimalDayRouteNames = findOptimalRoute(currentDayNames);
        List<LatLonPoint> optimalDayPoints = new ArrayList<>();
        for (String name : optimalDayRouteNames) {
            optimalDayPoints.add(attractionPoints.get(name));
        }

        // Update the data model
        dailyPlanNames.put(currentSelectedDay, optimalDayRouteNames);
        dailyPlans.put(currentSelectedDay, optimalDayPoints);

        Toast.makeText(this, getString(R.string.toast_added_to_day, poi.getTitle(), currentSelectedDay), Toast.LENGTH_SHORT).show();

        // **Refresh the entire display for the day**
        displayPlanForDay(currentSelectedDay);
    }
    private boolean shouldUseAMap() {
        // Check device locale country
        Locale locale = Locale.getDefault();
        String country = locale.getCountry().toUpperCase();

        // Use AMap if the phone language/region is set to China
        return country.equals("CN");
    }

    private void startSmartPlanning() {
        Toast.makeText(this, getString(R.string.toast_locating_city), Toast.LENGTH_SHORT).show();
        try {
            geocodeSearch = new GeocodeSearch(this);
            geocodeSearch.setOnGeocodeSearchListener(this);
            routeSearch = new RouteSearch(this);
            routeSearch.setRouteSearchListener(this);
            poiSearch = new PoiSearch(this, null);
            poiSearch.setOnPoiSearchListener(this);

            // 【修改点】不再判断语言，始终优先尝试高德
            // 如果高德失败，onGeocodeSearched 回调里会自动触发 geocodeCityWithGoogle
            Log.d(TAG, "Starting with AMap priority...");
            geocodeCityAndMoveCamera();

        } catch (AMapException e) {
            e.printStackTrace();
        }
    }

    private void geocodeCityAndMoveCamera() {
        Log.d(TAG, "Step 1: Geocoding city: " + city);
        GeocodeQuery query = new GeocodeQuery(city, city);
        geocodeSearch.getFromLocationNameAsyn(query);
    }

    // for English version
    private void geocodeCityWithGoogle(String cityName) {

        String encodedCity = Uri.encode(cityName);
        String urlStr = "https://maps.googleapis.com/maps/api/geocode/json?address="
                + encodedCity + "&key=AIzaSyAiEbD5dYtwZETZg9MLAmKg1EVrcJzA3PA";

        executorService.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String response = sb.toString();
                Log.e(TAG, "Google geocoding response: " + response);
                JSONObject jsonObject = new JSONObject(response);

                String status = jsonObject.getString("status");
                if ("OK".equals(status)) {
                    JSONObject location = jsonObject.getJSONArray("results")
                            .getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location");

                    double lat = location.getDouble("lat");
                    double lng = location.getDouble("lng");

                    runOnUiThread(() -> moveCameraToLocation(lat, lng));
                    runOnUiThread(this::poiSearchAllAttractions);

                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Google Geocoding failed: " + status, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Google Geocoding error", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void poiSearchAllAttractions() {

        Log.d(TAG, "Step 2: Starting POI search...");
        Toast.makeText(this, getString(R.string.toast_parsing_attraction), Toast.LENGTH_SHORT).show();

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
        Log.d(TAG, "Searching attraction: " + attractionName + " (" + currentSearchIndex + "/" + attractionsToSearch.size() + ")");

        // Add a short delay to prevent API overload
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            PoiSearch.Query query = new PoiSearch.Query(attractionName, "", city);
            query.setPageSize(1);
            query.setPageNum(0);
            poiSearch.setQuery(query);
            poiSearch.searchPOIAsyn();
        }, 400); // delay 400ms before next search
    }

    private void handleSearchPassFinished() {
        List<String> missingAttractions = new ArrayList<>();
        for (String name : originalAttractionNames) {
            if (!attractionPoints.containsKey(name)) {
                missingAttractions.add(name);
            }
        }

        if (!missingAttractions.isEmpty() && !hasRetried) {
            Log.d(TAG, "First pass failed. Retrying...");
            Toast.makeText(this, getString(R.string.toast_partial_search_failed), Toast.LENGTH_SHORT).show();

            this.hasRetried = true;
            this.attractionsToSearch = missingAttractions;
            this.currentSearchIndex = 0;
            poiSearchNextAttraction();

            return;
        }

        if (!missingAttractions.isEmpty()) {
            String missingStr = TextUtils.join(", ", missingAttractions);
            Toast.makeText(this, getString(R.string.toast_missing_attractions, missingStr), Toast.LENGTH_LONG).show();
        }

        if (attractionPoints.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_all_parsing_failed), Toast.LENGTH_LONG).show();
            return;
        }


        distributeAttractionsToDays();
        setupDaySwitcherUI();
    }

    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
        if (rCode == AMapException.CODE_AMAP_SUCCESS &&
                geocodeResult != null &&
                geocodeResult.getGeocodeAddressList() != null &&
                !geocodeResult.getGeocodeAddressList().isEmpty()) {

            GeocodeAddress address = geocodeResult.getGeocodeAddressList().get(0);
            LatLonPoint point = address.getLatLonPoint();
            moveCameraToLocation(point.getLatitude(), point.getLongitude());
            poiSearchAllAttractions();

        } else {
            // fallback to Google if AMap fails (for English names)
            geocodeCityWithGoogle(city);
        }
    }

    @Override
    public void onPoiSearched(PoiResult poiResult, int rCode) {
        // Case 1: Nearby search
        if (poiSearch.getBound() != null) {
            clearNearbyMarkers();
            if (rCode == AMapException.CODE_AMAP_SUCCESS) {
                if (poiResult != null && poiResult.getPois() != null && !poiResult.getPois().isEmpty()) {
                    for (PoiItem poi : poiResult.getPois()) {
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(new LatLng(poi.getLatLonPoint().getLatitude(), poi.getLatLonPoint().getLongitude()))
                                .title(poi.getTitle())
                                .snippet(getString(R.string.snippet_add_to_trip)) // InfoWindow content
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        Marker marker = aMap.addMarker(markerOptions);
                        nearbyPoiMarkers.add(marker);
                        markerPoiItemMap.put(marker, poi); // **Associate Marker with POI data**
                    }
                    Toast.makeText(this, getString(R.string.toast_marked_recommendations), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_nearby_places), Toast.LENGTH_SHORT).show();
                }

            }
            poiSearch.setBound(null);
        }
        // Case 2: Attraction parsing
        else {
            String currentAttractionName = attractionsToSearch.get(currentSearchIndex);
            if (rCode == AMapException.CODE_AMAP_SUCCESS
                    && poiResult != null
                    && poiResult.getPois() != null
                    && !poiResult.getPois().isEmpty()) {

                PoiItem poiItem = poiResult.getPois().get(0);
                attractionPoints.put(currentAttractionName, poiItem.getLatLonPoint());
                currentSearchIndex++;
                poiSearchNextAttraction();

            } else {
                // remember to change all strings
                Log.d(TAG, "AMap failed for " + currentAttractionName + ", trying Google...");
                searchAttractionWithGoogle(currentAttractionName);
            }

        }
    }

    private void searchNearbyWithGoogle(LatLonPoint centerPoint) {
        executorService.execute(() -> {
            try {
                String locationStr = centerPoint.getLatitude() + "," + centerPoint.getLongitude();
                String radius = "500"; // meters
                String type = "restaurant"; // or "food"
                String urlStr = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                        "location=" + locationStr +
                        "&radius=" + radius +
                        "&type=" + type +
                        "&key=AIzaSyAiEbD5dYtwZETZg9MLAmKg1EVrcJzA3PA";

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject jsonObject = new JSONObject(sb.toString());
                JSONArray results = jsonObject.getJSONArray("results");

                runOnUiThread(() -> {
                    clearNearbyMarkers(); // remove old markers
                    for (int i = 0; i < results.length(); i++) {
                        try {
                            JSONObject poi = results.getJSONObject(i);
                            JSONObject loc = poi.getJSONObject("geometry").getJSONObject("location");
                            double lat = loc.getDouble("lat");
                            double lng = loc.getDouble("lng");
                            String name = poi.getString("name");

                            MarkerOptions markerOptions = new MarkerOptions()
                                    .position(new LatLng(lat, lng))
                                    .title(name)
                                    .snippet(getString(R.string.snippet_add_to_trip))
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                            Marker marker = aMap.addMarker(markerOptions);
                            nearbyPoiMarkers.add(marker);
                            // We don't have PoiItem for Google, optionally store name->latlon mapping
                            markerPoiItemMap.put(marker, null);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Google nearby search failed", Toast.LENGTH_SHORT).show());
            }
        });
    }


    @Override
    public void onSearchNearbyClick(String attractionName) {
        LatLonPoint centerPoint = attractionPoints.get(attractionName);
        if (centerPoint == null) {
            Toast.makeText(this, getString(R.string.toast_no_location_info), Toast.LENGTH_SHORT).show();
            return;
        }

        // 【修改点】始终优先使用高德搜索周边
        // 高德的周边搜索在国内数据更丰富，且响应更快
        PoiSearch.Query query = new PoiSearch.Query(getString(R.string.poi_search_query), "050000", city);
        query.setPageSize(10);
        query.setPageNum(0);
        PoiSearch.SearchBound bound = new PoiSearch.SearchBound(centerPoint, 500);

        poiSearch.setBound(bound);
        poiSearch.setQuery(query);
        poiSearch.searchPOIAsyn();

        // 注意：目前的 onPoiSearched 回调中还没有为“搜周边”写 Google 的兜底逻辑
        // 如果高德搜不到，目前会显示“附近没有找到”。
        // 如果需要，可以在 onPoiSearched 的 Case 1 (rCode != SUCCESS) 中调用 searchNearbyWithGoogle(centerPoint)
    }


    private void setupRecyclerView() {
        attractionAdapter = new AttractionAdapter();
        attractionAdapter.setOnAttractionActionsListener(this);
        attractionAdapter.setStartDragListener(this); // Set drag listener
        binding.attractionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.attractionsRecyclerView.setAdapter(attractionAdapter);

        // **New: Initialize ItemTouchHelper and attach to RecyclerView**
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                attractionAdapter.onItemMove(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // We don't use swipe-to-delete
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView,viewHolder);
                // After drag ends, notify Activity to update the route
                onAttractionsReordered();
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.attractionsRecyclerView);
    }

    private void displayPlanForDay(int day) {
        this.currentSelectedDay = day;
        aMap.clear();
        markerPoiItemMap.clear();
        nearbyPoiMarkers.clear();

        // 【新增】每次刷新地图时，清空旧的行程点记录
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

            // 【修改】获取 Marker 对象，并存入 Map，key是Marker，value是景点名称
            Marker marker = aMap.addMarker(markerOptions);
            itineraryMarkerMap.put(marker, dayNames.get(i));
        }
        if (dayPoints.size() >= 2) {
            LatLonPoint from = dayPoints.get(0);
            LatLonPoint to = dayPoints.get(dayPoints.size() - 1);
            List<LatLonPoint> passby = (dayPoints.size() > 2) ? dayPoints.subList(1, dayPoints.size() - 1) : new ArrayList<>();
            RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(from, to);
            RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, passby, null, "");
            routeSearch.calculateDriveRouteAsyn(query);
        }
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(dayPoints.get(0).getLatitude(), dayPoints.get(0).getLongitude()), 12));
    }

    // 【新增方法 1】更新预算UI
    private void updateBudgetUI(int day) {
        if (currentTripId == -1) return;

        String currentDateStr = getTargetDateStr(day);

        binding.progressBarBudget.setOnClickListener(v -> showExpenseListDialog(currentDateStr));

        dbService.getDailyTotal(currentTripId, currentDateStr, total -> {
            runOnUiThread(() -> {
                binding.textExpense.setText(String.format(Locale.US, "%.2f / %.0f", total, dailyBudget));

                int progress = (int) ((total / dailyBudget) * 100);
                binding.progressBarBudget.setProgress(Math.min(progress, 100));

                // 超支变红
                if (total > dailyBudget) {
                    binding.progressBarBudget.getProgressDrawable().setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN);
                } else {
                    binding.progressBarBudget.getProgressDrawable().clearColorFilter();
                }
            });
        });
    }

    private void showExpenseListDialog(String dateStr) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Expenses for " + dateStr);

        // 使用 RecyclerView 显示列表
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        ExpenseAdapter adapter = new ExpenseAdapter();
        rv.setAdapter(adapter);

        // 设置边距
        int padding = 32;
        rv.setPadding(padding, padding, padding, padding);

        builder.setView(rv);
        builder.setPositiveButton("Close", null);
        AlertDialog dialog = builder.show();

        // 加载数据
        dbService.getDailyExpensesList(currentTripId, dateStr, list -> {
            if (list.isEmpty()) {
                Toast.makeText(this, "No expenses yet.", Toast.LENGTH_SHORT).show();
            } else {
                adapter.setExpenses(list);
            }
        });

        // 处理删除
        adapter.setOnExpenseDeleteListener(expense -> {
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Delete")
                    .setMessage("Delete this record?")
                    .setPositiveButton("Yes", (d, w) -> {
                        dbService.deleteExpense(expense);
                        // 刷新列表和外部UI
                        dbService.getDailyExpensesList(currentTripId, dateStr, adapter::setExpenses);
                        updateBudgetUI(currentSelectedDay);
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    // 【新增方法 2】显示记账弹窗
    private void showAddExpenseDialog() {
        if (currentTripId == -1) {
            Toast.makeText(this, "Loading trip info...", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);
        builder.setView(view);

        com.google.android.material.textfield.TextInputEditText inputAmount = view.findViewById(R.id.inputAmount);
        com.google.android.material.chip.ChipGroup chipGroup = view.findViewById(R.id.chipGroupCategory);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String amountStr = inputAmount.getText().toString();
            if (amountStr.isEmpty()) return;

            double amount = Double.parseDouble(amountStr);

            String category = "Other";
            int checkedId = chipGroup.getCheckedChipId();
            if (checkedId != -1) {
                com.google.android.material.chip.Chip chip = view.findViewById(checkedId);
                category = chip.getText().toString();
            }

            String dateStr = getTargetDateStr(currentSelectedDay);
            dbService.addExpense(currentTripId, category, amount, dateStr);

            Toast.makeText(this, "Expense Saved!", Toast.LENGTH_SHORT).show();
            updateBudgetUI(currentSelectedDay);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // 【新增方法 3】计算日期字符串
    private String getTargetDateStr(int day) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-d", Locale.getDefault());
        try {
            Date startDate = sdf.parse(startDateStr);
            calendar.setTime(startDate);
            calendar.add(Calendar.DAY_OF_YEAR, day - 1);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private void clearNearbyMarkers(){
        for(Marker marker : nearbyPoiMarkers){
            marker.remove();
        }
        nearbyPoiMarkers.clear();
        markerPoiItemMap.clear();
    }

    private void moveCameraToLocation(double latitude, double longitude) {
        if (aMap != null) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 11));
        }
    }
    private List<String> sortAttractionsByNearestNeighbor(List<String> attractions) {
        if (attractions.size() < 2) return attractions;
        List<String> sortedList = new ArrayList<>();
        List<String> unvisited = new ArrayList<>(attractions);
        String currentAttraction = unvisited.get(0);
        sortedList.add(currentAttraction);
        unvisited.remove(currentAttraction);
        while (!unvisited.isEmpty()) {
            final String finalCurrentAttraction = currentAttraction;
            String nearest = Collections.min(unvisited, (a1, a2) -> {
                LatLonPoint p_current = attractionPoints.get(finalCurrentAttraction);
                LatLonPoint p1 = attractionPoints.get(a1);
                LatLonPoint p2 = attractionPoints.get(a2);
                float dist1 = AMapUtils.calculateLineDistance(new LatLng(p_current.getLatitude(), p_current.getLongitude()), new LatLng(p1.getLatitude(), p1.getLongitude()));
                float dist2 = AMapUtils.calculateLineDistance(new LatLng(p_current.getLatitude(), p_current.getLongitude()), new LatLng(p2.getLatitude(), p2.getLongitude()));
                return Float.compare(dist1, dist2);
            });
            currentAttraction = nearest;
            sortedList.add(currentAttraction);
            unvisited.remove(currentAttraction);
        }
        return sortedList;
    }

    private void distributeAttractionsToDays() {
        List<String> sortedAttractionNames = sortAttractionsByNearestNeighbor(new ArrayList<>(attractionPoints.keySet()));
        int attractionsPerDay = (int) Math.ceil((double) sortedAttractionNames.size() / totalDays);
        for (int i = 0; i < totalDays; i++) {
            List<String> dayNames = new ArrayList<>();
            int startIndex = i * attractionsPerDay;
            int endIndex = Math.min(startIndex + attractionsPerDay, sortedAttractionNames.size());
            dayNames.addAll(sortedAttractionNames.subList(startIndex, endIndex));
            if (!dayNames.isEmpty()) {
                List<String> optimalDayRouteNames = findOptimalRoute(dayNames);
                List<LatLonPoint> optimalDayPoints = new ArrayList<>();
                for (String name : optimalDayRouteNames) {
                    optimalDayPoints.add(attractionPoints.get(name));
                }
                dailyPlans.put(i + 1, optimalDayPoints);
                dailyPlanNames.put(i + 1, optimalDayRouteNames);
            }
        }
    }

    private List<String> findOptimalRoute(List<String> points) {
        if (points.size() <= 2) return points;
        List<List<String>> allPermutations = new ArrayList<>();
        generatePermutations(points, 0, allPermutations);
        List<String> bestRoute = null;
        float minDistance = Float.MAX_VALUE;
        for (List<String> route : allPermutations) {
            float currentDistance = calculateTotalDistance(route);
            if (currentDistance < minDistance) {
                minDistance = currentDistance;
                bestRoute = route;
            }
        }
        return bestRoute;
    }

    private void generatePermutations(List<String> arr, int k, List<List<String>> result) {
        if (k == arr.size() - 1) {
            result.add(new ArrayList<>(arr));
        } else {
            for (int i = k; i < arr.size(); i++) {
                Collections.swap(arr, i, k);
                generatePermutations(arr, k + 1, result);
                Collections.swap(arr, k, i);
            }
        }
    }

    private float calculateTotalDistance(List<String> route) {
        float totalDistance = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            LatLonPoint p1 = attractionPoints.get(route.get(i));
            LatLonPoint p2 = attractionPoints.get(route.get(i+1));
            totalDistance += AMapUtils.calculateLineDistance(new LatLng(p1.getLatitude(), p1.getLongitude()), new LatLng(p2.getLatitude(), p2.getLongitude()));
        }
        return totalDistance;
    }

    private void setupDaySwitcherUI() {
        // get region
        Locale locale = Locale.getDefault();

        binding.dayChipGroup.removeAllViews();

        Calendar calendar = Calendar.getInstance();

        SimpleDateFormat sdf;

        // TODO - fix it so that the date changes format based on region
        if(locale.getLanguage().equals("zh")) {
            sdf = new SimpleDateFormat("yyyy-M-d", Locale.getDefault());
        } else {
            sdf = new SimpleDateFormat("d-M-yyyy", Locale.getDefault());
        }

        try {
            Date startDate = sdf.parse(startDateStr);
            calendar.setTime(startDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        // Load format string from resources (auto-localized)
        String datePattern = getString(R.string.date_format);
        SimpleDateFormat chipSdf = new SimpleDateFormat(datePattern, Locale.getDefault());

        for (int i = 1; i <= totalDays; i++) {
            Chip chip = new Chip(this);
            String dateText = chipSdf.format(calendar.getTime());

            // Use string resource for day prefix
            String dayPrefix = getString(R.string.day_label, i);

            chip.setText(dayPrefix + " (" + dateText + ")");
            chip.setCheckable(true);
            chip.setId(i);
            binding.dayChipGroup.addView(chip);

            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        binding.dayChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int selectedDay = checkedIds.get(0);
            this.currentSelectedDay = selectedDay;
            displayPlanForDay(selectedDay);
        });

        if (binding.dayChipGroup.getChildCount() > 0) {
            binding.dayChipGroup.check(binding.dayChipGroup.getChildAt(0).getId());
        }
    }


    private BitmapDescriptor getCustomMarker(String text) {
        View view = getLayoutInflater().inflate(R.layout.marker_layout, null);
        TextView textView = view.findViewById(R.id.marker_text);
        textView.setText(text);
        view.setDrawingCacheEnabled(true);
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult result, int errorCode) {
        if (errorCode == AMapException.CODE_AMAP_SUCCESS && result != null && result.getPaths() != null && !result.getPaths().isEmpty()) {
            DrivePath drivePath = result.getPaths().get(0);
            if (drivePath == null) return;
            List<LatLng> routePath = new ArrayList<>();
            drivePath.getSteps().forEach(step ->
                    step.getPolyline().forEach(point ->
                            routePath.add(new LatLng(point.getLatitude(), point.getLongitude()))
                    )
            );
            aMap.addPolyline(new PolylineOptions().addAll(routePath).width(20f).color(Color.WHITE));
            aMap.addPolyline(new PolylineOptions().addAll(routePath).width(16f).color(Color.argb(255, 1, 159, 241)));
        }
    }

    private void searchAttractionWithGoogle(String attractionName) {
        Log.d(TAG, "searchAttractionWithGoogle START for: " + attractionName + " (index=" + currentSearchIndex + ")");
        executorService.execute(() -> {
            try {
                String encodedName = Uri.encode(attractionName + " in " + city);
                String urlStr = "https://maps.googleapis.com/maps/api/place/textsearch/json?query="
                        + encodedName + "&key=AIzaSyAiEbD5dYtwZETZg9MLAmKg1EVrcJzA3PA";

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject jsonObject = new JSONObject(sb.toString());
                String status = jsonObject.getString("status");

                if ("OK".equals(status)) {
                    JSONObject firstResult = jsonObject.getJSONArray("results").getJSONObject(0);
                    JSONObject location = firstResult.getJSONObject("geometry").getJSONObject("location");
                    double lat = location.getDouble("lat");
                    double lng = location.getDouble("lng");

                    LatLonPoint point = new LatLonPoint(lat, lng);
                    attractionPoints.put(attractionName, point);

                    Log.d(TAG, "Google found: " + attractionName + " -> " + lat + "," + lng);
                } else {
                    Log.w(TAG, "Google Places search failed for " + attractionName + ": " + status);
                }

            } catch (Exception e) {
                Log.e(TAG, "Google Places exception for " + attractionName, e);
            } finally {
                runOnUiThread(() -> {
                    currentSearchIndex++;
                    Log.d(TAG, "Google search done for: " + attractionName + " (nextIndex=" + currentSearchIndex + ")");
                    poiSearchNextAttraction();
                });
            }
        });
    }


    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {}
    @Override
    public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int i) {}
    @Override
    public void onBusRouteSearched(BusRouteResult busRouteResult, int i) {}
    @Override
    public void onWalkRouteSearched(WalkRouteResult walkRouteResult, int i) {}
    @Override
    public void onRideRouteSearched(RideRouteResult rideRouteResult, int i) {}
    @Override
    protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override
    protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override
    protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override
    protected void onSaveInstanceState(Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
}