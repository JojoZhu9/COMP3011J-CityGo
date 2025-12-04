package com.example.citygo;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class WeatherService {

    public interface WeatherCallback {
        void onSuccess(String text, int iconResId);
        void onError(String error);
    }

    public static void fetchWeather(Context context, double lat, double lon, String dateStr, WeatherCallback callback) {
        new Thread(() -> {
            try {
                // 构造 URL (Open-Meteo)
                String urlStr = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=auto&start_date=%s&end_date=%s",
                        lat, lon, dateStr, dateStr);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) buffer.append(line);

                JSONObject jsonObject = new JSONObject(buffer.toString());
                JSONObject daily = jsonObject.getJSONObject("daily");

                int code = daily.getJSONArray("weathercode").getInt(0);
                double max = daily.getJSONArray("temperature_2m_max").getDouble(0);
                double min = daily.getJSONArray("temperature_2m_min").getDouble(0);

                String desc = getWeatherDescription(code);
                int iconId = getWeatherIconResource(context, code);
                String resultText = String.format(Locale.getDefault(), "%s  %.0f°C / %.0f°C", desc, min, max);

                callback.onSuccess(resultText, iconId);

            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static String getWeatherDescription(int code) {
        if (code == 0) return "Clear sky";
        if (code >= 1 && code <= 3) return "Cloudy";
        if (code >= 51 && code <= 67) return "Rain";
        if (code >= 71) return "Snow";
        return "Unknown";
    }

    private static int getWeatherIconResource(Context context, int code) {
        String name = "ic_qweather_100";
        if (code >= 1 && code <= 3) name = "ic_qweather_101";
        else if (code >= 51) name = "ic_qweather_305";
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }
}