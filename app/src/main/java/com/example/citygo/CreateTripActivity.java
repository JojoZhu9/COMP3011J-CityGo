package com.example.citygo;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTripBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbService = new DBService(this);

        binding.startDateButton.setOnClickListener(v -> showDatePickerDialog());
        binding.generatePlanButton.setOnClickListener(v -> generatePlan());
        binding.btnAiAssist.setOnClickListener(v -> showAIDialog());
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
                String systemPrompt = "You are a professional travel planning assistant. Current date is " + today + ". " +
                        "Please generate a travel plan based on user requirements. " +
                        "You must strictly return pure JSON format data without markdown tags (like ```json). " +
                        "The JSON must contain the following fields: " +
                        "1. city (Target city name, in English) " +
                        "2. attractions (5-8 recommended attractions, comma-separated string, in English) " +
                        "3. days (Suggested duration, integer) " +
                        "4. startDate (Departure date, format yyyy-MM-dd. Calculate based on user input, default to tomorrow if not specified). " +
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

                    // 6. Update UI on Main Thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        binding.cityEditText.setText(aiCity);
                        binding.attractionsEditText.setText(aiAttractions);
                        binding.daysEditText.setText(String.valueOf(aiDays));

                        selectedDate = aiDate;
                        binding.startDateButton.setText(aiDate);

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
            dbService.saveTrip(userEmail, city, attractions, days, selectedDate);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Days format error");
        }

        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("EXTRA_CITY", city);
        intent.putExtra("EXTRA_ATTRACTIONS", attractions);
        intent.putExtra("EXTRA_DAYS", Integer.parseInt(daysStr));
        intent.putExtra("EXTRA_START_DATE", selectedDate);
        startActivity(intent);
        finish();
    }
}