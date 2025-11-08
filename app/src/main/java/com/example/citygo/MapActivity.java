package com.example.citygo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
    private static final String HEWEATHER_API_KEY = "8e2c8be628054145ac5d43fbdc4b38e3";
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

    // **新增：用于存储和管理推荐点的 Marker**
    private List<Marker> nearbyPoiMarkers = new ArrayList<>();
    // **新增：将 Marker 与其对应的 POI 数据关联起来**
    private Map<Marker, PoiItem> markerPoiItemMap = new HashMap<>();
    // **新增：记录当前选中的是第几天**
    private int currentSelectedDay = 1;
    private ItemTouchHelper itemTouchHelper;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.getUiSettings().setZoomControlsEnabled(true);
            // **新增：设置点击监听器**
            aMap.setOnMarkerClickListener(this);
            aMap.setOnInfoWindowClickListener(this);
        }
        setupRecyclerView();
        Intent intent = getIntent();
        city = intent.getStringExtra("EXTRA_CITY");
        String attractionsStr = intent.getStringExtra("EXTRA_ATTRACTIONS");
        totalDays = intent.getIntExtra("EXTRA_DAYS", 0);
        startDateStr = intent.getStringExtra("EXTRA_START_DATE");

        originalAttractionNames = new ArrayList<>();
        for (String name : attractionsStr.split("[,，]")) {
            if (!name.trim().isEmpty()) {
                originalAttractionNames.add(name.trim());
            }
        }
        Log.d(TAG, "Loaded attractions: " + originalAttractionNames);
        if (totalDays == 0 || originalAttractionNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_incomplete_trip), Toast.LENGTH_LONG).show();
            return;
        }

        startSmartPlanning();
    }
    private void fetchWeatherForDay(int day) {
        List<LatLonPoint> dayPoints = dailyPlans.get(day);
        if (dayPoints == null || dayPoints.isEmpty()) {
            binding.weatherText.setText(getString(R.string.weather_no_info));
            binding.weatherIcon.setImageResource(0);
            return;
        }

        LatLonPoint location = dayPoints.get(0);
        // 和风天气的 location 参数格式是 "经度,纬度"
        String locationStr = String.format(Locale.US, "%.2f,%.2f", location.getLongitude(), location.getLatitude());

        // 计算目标日期
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-d", Locale.getDefault());
        try {
            Date startDate = sdf.parse(startDateStr);
            calendar.setTime(startDate);
            calendar.add(Calendar.DAY_OF_YEAR, day - 1);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        String targetDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());

        executorService.execute(() -> {
            try {
                // 使用和风天气 3天预报 API
                URL url = new URL(String.format("https://devapi.qweather.com/v7/weather/3d?location=%s&key=%s", locationStr, HEWEATHER_API_KEY));

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream stream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                }
                String jsonResponse = buffer.toString();
                Log.d(TAG, "HeWeather Response: " + jsonResponse);

                JSONObject jsonObject = new JSONObject(jsonResponse);
                JSONArray dailyForecasts = jsonObject.getJSONArray("daily");

                String tempMin = "--";
                String tempMax = "--";
                String textDay = getString(R.string.text_day);
                String icon = "";

                // 遍历返回的3天预报，找到与我们目标日期匹配的那一天
                for (int i = 0; i < dailyForecasts.length(); i++) {
                    JSONObject dayForecast = dailyForecasts.getJSONObject(i);
                    String fxDate = dayForecast.getString("fxDate"); // "2025-10-18"

                    if (fxDate.equals(targetDateStr)) {
                        tempMin = dayForecast.getString("tempMin");
                        tempMax = dayForecast.getString("tempMax");
                        textDay = dayForecast.getString("textDay");
                        icon = dayForecast.getString("iconDay");
                        break; // 找到后就跳出循环
                    }
                }

                final String weatherInfo = textDay + "  " + tempMin + "°C / " + tempMax + "°C";
                final String iconName = "ic_qweather_" + icon;

                runOnUiThread(() -> {
                    binding.weatherText.setText(weatherInfo);
                    int iconId = getResources().getIdentifier(iconName, "drawable", getPackageName());
                    if (iconId != 0) {
                        binding.weatherIcon.setImageResource(iconId);
                    } else {
                        binding.weatherIcon.setImageResource(0);
                        Log.w(TAG, "Weather icon not found: " + iconName);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> binding.weatherText.setText(getString(R.string.toast_weather_failed)));
            }
        });
    }

    // 实现移除地点的监听方法
    @Override
    public void onAttractionRemoved(int position) {
        List<String> currentDayNames = dailyPlanNames.get(currentSelectedDay);
        if (currentDayNames == null || position >= currentDayNames.size()) return;

        String attractionToRemove = currentDayNames.get(position);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, attractionToRemove))
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> {
                    // 从数据模型中移除
                    currentDayNames.remove(position);
                    dailyPlans.get(currentSelectedDay).remove(position);

                    // 重新规划当天的最优路径
                    List<String> optimalDayRouteNames = findOptimalRoute(currentDayNames);
                    List<LatLonPoint> optimalDayPoints = new ArrayList<>();
                    for (String name : optimalDayRouteNames) {
                        optimalDayPoints.add(attractionPoints.get(name));
                    }
                    dailyPlanNames.put(currentSelectedDay, optimalDayRouteNames);
                    dailyPlans.put(currentSelectedDay, optimalDayPoints);

                    // 刷新地图和列表
                    displayPlanForDay(currentSelectedDay);
                })
                .setNegativeButton(R.string.dialog_delete_cancel, null)
                .show();
    }

    // 实现拖拽结束的监听方法
    @Override
    public void onAttractionsReordered() {
        // 当拖拽结束后，Adapter 里的列表顺序已经是新的了
        List<String> newOrder = attractionAdapter.getAttractionNames();

        // 更新我们的数据模型
        dailyPlanNames.put(currentSelectedDay, new ArrayList<>(newOrder));
        List<LatLonPoint> newPointsOrder = new ArrayList<>();
        for (String name : newOrder) {
            newPointsOrder.add(attractionPoints.get(name));
        }
        dailyPlans.put(currentSelectedDay, newPointsOrder);

        // **直接用新的顺序重新规划路线并刷新，不再需要跑TSP**
        displayPlanForDay(currentSelectedDay);
    }

    // 实现开始拖拽的监听方法
    @Override
    public void requestDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

    // **新增：实现 Marker 点击事件**
    @Override
    public boolean onMarkerClick(Marker marker) {
        // 检查这个被点击的 marker 是不是一个“推荐点”（绿色图钉）
        if (markerPoiItemMap.containsKey(marker)) {
            // 是推荐点，显示它的信息窗
            marker.showInfoWindow();
        }
        // 返回 false 表示我们没有完全消费这个事件，地图SDK可以继续执行默认行为（比如移动地图中心）
        return false;
    }

    // **新增：实现 InfoWindow 点击事件**
    @Override
    public void onInfoWindowClick(Marker marker) {
        // 再次确认这是一个“推荐点”
        if (markerPoiItemMap.containsKey(marker)) {
            PoiItem poiToAdd = markerPoiItemMap.get(marker);
            addPoiToCurrentDay(poiToAdd);
            marker.hideInfoWindow(); // 添加后隐藏信息窗
        }
    }

    // **新增：将 POI 添加到当天行程的核心逻辑**
    private void addPoiToCurrentDay(PoiItem poi) {
        if (poi == null) return;

        // 获取当前日的行程列表
        List<String> currentDayNames = dailyPlanNames.get(currentSelectedDay);
        if (currentDayNames == null) {
            currentDayNames = new ArrayList<>();
        }

        // 检查是否已添加
        if (currentDayNames.contains(poi.getTitle())) {
            Toast.makeText(this, getString(R.string.toast_already_in_trip, poi.getTitle()), Toast.LENGTH_SHORT).show();
            return;
        }

        // 添加新的地点
        currentDayNames.add(poi.getTitle());
        attractionPoints.put(poi.getTitle(), poi.getLatLonPoint());

        // **关键一步：重新对当天的行程进行最优路径计算**
        List<String> optimalDayRouteNames = findOptimalRoute(currentDayNames);
        List<LatLonPoint> optimalDayPoints = new ArrayList<>();
        for (String name : optimalDayRouteNames) {
            optimalDayPoints.add(attractionPoints.get(name));
        }

        // 更新数据模型
        dailyPlanNames.put(currentSelectedDay, optimalDayRouteNames);
        dailyPlans.put(currentSelectedDay, optimalDayPoints);

        Toast.makeText(this, getString(R.string.toast_added_to_day, poi.getTitle(), currentSelectedDay), Toast.LENGTH_SHORT).show();

        // **刷新整个当天的显示**
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

            if (shouldUseAMap()) {
                Log.d(TAG, "Detected Chinese region — using AMap geocoding...");
                geocodeCityAndMoveCamera();
            } else {
                Log.d(TAG, "Detected non-Chinese region — using Google geocoding...");
                geocodeCityWithGoogle(city);
            }

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
        // Case 1: 周边搜索
        if (poiSearch.getBound() != null) {
            clearNearbyMarkers();
            if (rCode == AMapException.CODE_AMAP_SUCCESS) {
                if (poiResult != null && poiResult.getPois() != null && !poiResult.getPois().isEmpty()) {
                    for (PoiItem poi : poiResult.getPois()) {
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(new LatLng(poi.getLatLonPoint().getLatitude(), poi.getLatLonPoint().getLongitude()))
                                .title(poi.getTitle())
                                .snippet(getString(R.string.snippet_add_to_trip)) // InfoWindow 的内容
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        Marker marker = aMap.addMarker(markerOptions);
                        nearbyPoiMarkers.add(marker);
                        markerPoiItemMap.put(marker, poi); // **关联 Marker 和 POI 数据**
                    }
                    Toast.makeText(this, getString(R.string.toast_marked_recommendations), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_nearby_places), Toast.LENGTH_SHORT).show();
                }

            }
            poiSearch.setBound(null);
        }
        // Case 2: 景点解析
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

        if (shouldUseAMap()) {
            PoiSearch.Query query = new PoiSearch.Query(getString(R.string.poi_search_query), "050000", city);
            query.setPageSize(10);
            query.setPageNum(0);
            PoiSearch.SearchBound bound = new PoiSearch.SearchBound(centerPoint, 500);

            poiSearch.setBound(bound);
            poiSearch.setQuery(query);
            poiSearch.searchPOIAsyn();
        } else {
            searchNearbyWithGoogle(centerPoint);
        }
    }


    private void setupRecyclerView() {
        attractionAdapter = new AttractionAdapter();
        attractionAdapter.setOnAttractionActionsListener(this);
        attractionAdapter.setStartDragListener(this); // 设置拖拽监听
        binding.attractionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.attractionsRecyclerView.setAdapter(attractionAdapter);

        // **新增：初始化 ItemTouchHelper 并附加到 RecyclerView**
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
                // 我们不使用滑动删除
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView,viewHolder);
                // 拖拽结束后，通知 Activity 更新路线
                onAttractionsReordered();
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.attractionsRecyclerView);
    }

    private void displayPlanForDay(int day) {
        this.currentSelectedDay = day; // **记录当前天数**
        aMap.clear();
        markerPoiItemMap.clear(); // 清空旧的关联
        nearbyPoiMarkers.clear();

        fetchWeatherForDay(day);

        List<LatLonPoint> dayPoints = dailyPlans.get(day);
        List<String> dayNames = dailyPlanNames.get(day);

        attractionAdapter.updateData(dayNames);
        if (dayPoints == null || dayPoints.isEmpty()) return;

        for (int i = 0; i < dayPoints.size(); i++) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(new LatLng(dayPoints.get(i).getLatitude(), dayPoints.get(i).getLongitude()))
                    .title(dayNames.get(i))
                    .icon(getCustomMarker(String.valueOf(i + 1)));
            aMap.addMarker(markerOptions);
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