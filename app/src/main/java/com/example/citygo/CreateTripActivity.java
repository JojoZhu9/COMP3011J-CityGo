package com.example.citygo;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.citygo.databinding.ActivityCreateTripBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;


public class CreateTripActivity extends AppCompatActivity {

    private static final String TAG = "CreateTripDebug";

    // ==========================================
    // 1. Paste your DeepSeek API Key here
    // ==========================================
    private static final String DEEPSEEK_API_KEY = "sk-d51b987e1be546148868cc1fc988d52e";
    private static final String API_URL = "https://api.deepseek.com/chat/completions";

    private ActivityCreateTripBinding binding;
    private String selectedDate = null;
    private DBService dbService;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private com.google.android.libraries.places.api.model.RectangularBounds cityBounds = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTripBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Places.initialize(getApplicationContext(), "AIzaSyAR3DCQQ26plX8A7OUwAVp5lWWr_4hw1yE");
        PlacesClient placesClient = Places.createClient(this);

        dbService = new DBService(this);

        // 【修改】当 City 输入框失去焦点时，去获取城市坐标，锁定 Hotel 搜索范围
        binding.cityEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateCityBounds(binding.cityEditText.getText().toString().trim());
            }
        });

        setupHotelAutocomplete(placesClient);

        binding.startDateButton.setOnClickListener(v -> showDatePickerDialog());
        binding.generatePlanButton.setOnClickListener(v -> generatePlan());
        binding.btnAiAssist.setOnClickListener(v -> showAIDialog());
    }

    private void updateCityBounds(String cityName) {
        if (TextUtils.isEmpty(cityName)) return;

        executorService.execute(() -> {
            try {
                // 使用你的 Google Key
                String urlStr = "https://maps.googleapis.com/maps/api/geocode/json?address="
                        + Uri.encode(cityName)
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
                    JSONObject viewport = json.getJSONArray("results")
                            .getJSONObject(0).getJSONObject("geometry").getJSONObject("viewport");

                    JSONObject northeast = viewport.getJSONObject("northeast");
                    JSONObject southwest = viewport.getJSONObject("southwest");

                    // 构建矩形范围
                    cityBounds = com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                            new com.google.android.gms.maps.model.LatLng(southwest.getDouble("lat"), southwest.getDouble("lng")),
                            new com.google.android.gms.maps.model.LatLng(northeast.getDouble("lat"), northeast.getDouble("lng"))
                    );
                    Log.d(TAG, "City bounds updated for: " + cityName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupHotelAutocomplete(PlacesClient placesClient) {
        AutoCompleteTextView hotelAutoComplete = binding.hotelAutoComplete;
        AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();

        hotelAutoComplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 只要有输入就尝试触发，不设置过高的阈值
                if (s.length() < 1) return;

                FindAutocompletePredictionsRequest.Builder requestBuilder = FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(token)
                        .setQuery(s.toString())
                        .setTypesFilter(java.util.Arrays.asList("lodging")); // 仅搜索住宿

                // 如果已经获取到城市范围，则应用偏好
                if (cityBounds != null) {
                    requestBuilder.setLocationBias(cityBounds);
                }

                placesClient.findAutocompletePredictions(requestBuilder.build()).addOnSuccessListener(response -> {
                    List<String> hotelNames = new ArrayList<>();
                    for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                        hotelNames.add(prediction.getPrimaryText(null).toString());
                    }

                    // 【核心修复】确保在主线程更新 UI
                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(CreateTripActivity.this,
                                android.R.layout.simple_dropdown_item_1line, hotelNames);
                        hotelAutoComplete.setAdapter(adapter);
                        adapter.notifyDataSetChanged();

                        // 【关键】强制显示下拉列表，防止被输入法遮挡或不弹出
                        if (hotelAutoComplete.hasFocus() && !hotelNames.isEmpty()) {
                            hotelAutoComplete.showDropDown();
                        }
                    });

                }).addOnFailureListener(exception -> {
                    Log.e(TAG, "Autocomplete failed: " + exception.getMessage());
                });
            }
        });
    }


    // --- AI Feature Area ---

    private void showAIDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Changed to English
        builder.setTitle("✨ AI Trip Assistant");
        builder.setMessage("Please describe your trip wish (e.g., I want to visit Paris for 3 days to see museums, starting tomorrow):");

        final EditText input = new EditText(this);
        input.setHint("Enter your wish here...");
        input.setPadding(40, 40, 40, 40);
        builder.setView(input);

        builder.setPositiveButton("Generate Plan", (dialog, which) -> {
            String userRequest = input.getText().toString();
            if (!userRequest.trim().isEmpty()) {
                callDeepSeekAPI(userRequest);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void callDeepSeekAPI(String userRequest) {
        Toast.makeText(this, "AI is thinking, please wait...", Toast.LENGTH_SHORT).show();

        // Get User Preferences
        UserPrefs prefs = new UserPrefs(this);
        String interests = prefs.getInterests().toString();
        String dietary = prefs.getDietary().toString();

        // Get today's date for relative date calculation (e.g., "tomorrow")
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        executorService.execute(() -> {
            try {
                // 1. Construct System Prompt (IN ENGLISH)
                // We explicitly tell the AI to return English content.
                // 1. Construct System Prompt (IN ENGLISH)
                String systemPrompt = "You are a professional travel planning assistant. Current date is " + today + ". " +
                        "Please generate a travel plan based on user requirements. " +

                        // --- 修改开始：核心约束 ---
                        "CRITICAL LOCATION RULES: " +
                        "1. SCOPE: The hotel and ALL attractions must be geographically located STRICTLY within the target 'city'. Do not suggest attractions from neighboring cities or regions. " +
                        "2. NAMING: Use the official, standard English names for attractions and hotels exactly as they appear on Google Maps or AMap. " +
                        "   - Bad example: 'The beautiful ancient palace in Beijing' " +
                        "   - Good example: 'The Palace Museum' " +
                        "   - Do not include adjectives, descriptions, or prefixes like 'Visit the...'. " +
                        // --- 修改结束 ---

                        "Each day must start and end at the hotel. " +
                        "You must strictly return pure JSON format data without markdown tags (like ```json). " +
                        "The JSON must contain the following fields: " +
                        "1. city (Target city name, in English) " +
                        "2. hotel (A real, existing Hotel name located in the city, in English) " +
                        "3. attractions (5-8 recommended attractions, comma-separated string, in English. Must be distinct, real place names.) " +
                        "4. days (Suggested duration, integer) " +
                        "5. startDate (Departure date, format yyyy-MM-dd. Calculate based on user input, default to tomorrow if not specified). " +
                        "Ensure all text values are in English.";

                String userMessage = "User Request: " + userRequest + ". User Interests: " + interests + ". Dietary Preferences: " + dietary;

                // 2. Construct API Request Body
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "deepseek-chat");
                jsonBody.put("temperature", 1.0);

                JSONArray messages = new JSONArray();
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);

                JSONObject usrMsg = new JSONObject();
                usrMsg.put("role", "user");
                usrMsg.put("content", userMessage);
                messages.put(usrMsg);

                jsonBody.put("messages", messages);
                // Force JSON mode
                jsonBody.put("response_format", new JSONObject().put("type", "json_object"));

                Log.d(TAG, "Request Body: " + jsonBody.toString());

                // 3. Send HTTP POST Request
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + DEEPSEEK_API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 4. Read Response
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    Log.d(TAG, "API Response: " + response.toString());

                    // 5. Parse Response
                    JSONObject responseJson = new JSONObject(response.toString());
                    String content = responseJson.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    content = cleanJsonString(content);
                    Log.d(TAG, "Cleaned Content: " + content);

                    JSONObject tripPlan = new JSONObject(content);
                    String aiCity = tripPlan.optString("city");
                    String aiAttractions = tripPlan.optString("attractions");
                    int aiDays = tripPlan.optInt("days");
                    String aiDate = tripPlan.optString("startDate");
                    String aiHotel = tripPlan.optString("hotel");

                    // 6. Update UI on Main Thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        binding.cityEditText.setText(aiCity);
                        binding.attractionsEditText.setText(aiAttractions);
                        binding.daysEditText.setText(String.valueOf(aiDays));

                        selectedDate = aiDate;
                        binding.startDateButton.setText(aiDate);

                        binding.hotelAutoComplete.setText(aiHotel);

                        Toast.makeText(CreateTripActivity.this, "AI generated plan for " + aiCity + "!", Toast.LENGTH_LONG).show();
                    });

                } else {
                    Log.e(TAG, "Error Response Code: " + responseCode);
                    runOnUiToast("AI Request Failed. Check network or Key.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Exception: " + e.getMessage());
                runOnUiToast("AI Error: " + e.getMessage());
            }
        });
    }

    private String cleanJsonString(String json) {
        if (json.startsWith("```json")) {
            json = json.substring(7);
        }
        if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }

    private void runOnUiToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(CreateTripActivity.this, msg, Toast.LENGTH_SHORT).show()
        );
    }

    // --- Original Logic Below ---

    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    // Format date as yyyy-M-d
                    selectedDate = year1 + "-" + (monthOfYear + 1) + "-" + dayOfMonth;
                    binding.startDateButton.setText(selectedDate);
                }, year, month, day);
        datePickerDialog.show();
    }

    private void generatePlan() {
        String city = binding.cityEditText.getText().toString().trim();
        String attractions = binding.attractionsEditText.getText().toString().trim();
        String hotel = binding.hotelAutoComplete.getText().toString().trim();
        String daysStr = binding.daysEditText.getText().toString().trim();

        if (TextUtils.isEmpty(city) || TextUtils.isEmpty(attractions) || TextUtils.isEmpty(daysStr) || selectedDate == null) {
            // Changed Toast to English
            Toast.makeText(this, "Please fill in all information, including start date.", Toast.LENGTH_SHORT).show();
            return;
        }

        UserPrefs prefs = new UserPrefs(this);
        String userEmail = prefs.getEmail();
        if (TextUtils.isEmpty(userEmail)) userEmail = "anonymous@citygo.com";

        try {
            int days = Integer.parseInt(daysStr);
            dbService.saveTrip(userEmail, city, attractions, days, selectedDate, hotel);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Days format error");
        }

        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("EXTRA_CITY", city);
        intent.putExtra("EXTRA_HOTEL", hotel);
        intent.putExtra("EXTRA_ATTRACTIONS", attractions);
        intent.putExtra("EXTRA_DAYS", Integer.parseInt(daysStr));
        intent.putExtra("EXTRA_START_DATE", selectedDate);
        startActivity(intent);
        finish();
    }
}