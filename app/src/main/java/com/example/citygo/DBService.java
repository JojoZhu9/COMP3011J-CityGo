package com.example.citygo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.citygo.database.AppDao;
import com.example.citygo.database.AppDatabase;
import com.example.citygo.database.Expense;
import com.example.citygo.database.Trip;
import com.example.citygo.database.User;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DBService {

    private static final String TAG = "DBService_Local";
    private final AppDao appDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DataCallback<T> {
        void onDataLoaded(T data);
    }

    public DBService(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        appDao = db.appDao();
    }

    // --- 用户 ---
    public void saveUserProfile(String name, String email, String homeCity, int budget, String dietaryPrefs) {
        executor.execute(() -> {
            User user = new User(email, name, homeCity, budget, dietaryPrefs);
            appDao.insertUser(user);
        });
    }

    public void getUserProfile(String email, DataCallback<User> callback) {
        executor.execute(() -> {
            User user = appDao.getUser(email);
            mainHandler.post(() -> callback.onDataLoaded(user));
        });
    }

    // --- 行程 ---
    public void saveTrip(String email, String city, String attractions, int days, String startDate, String hotel) {
        executor.execute(() -> {
            Trip trip = new Trip(email, city, attractions, days, startDate, hotel);
            appDao.insertTrip(trip);
            Log.d(TAG, "Trip saved locally: " + city);
        });
    }

    public void getUserTrips(String email, DataCallback<List<Trip>> callback) {
        executor.execute(() -> {
            List<Trip> trips = appDao.getUserTrips(email);
            mainHandler.post(() -> callback.onDataLoaded(trips));
        });
    }

    public void deleteTrip(Trip trip) {
        executor.execute(() -> appDao.deleteTrip(trip));
    }

    // --- 【新增】记账相关 ---

    // 1. 获取当前的 Trip 对象 (为了拿到 tripId)
    public void getTrip(String city, String startDate, DataCallback<Trip> callback) {
        executor.execute(() -> {
            Trip trip = appDao.getTripByCityAndDate(city, startDate);
            mainHandler.post(() -> callback.onDataLoaded(trip));
        });
    }

    // 2. 添加一笔消费
    public void addExpense(int tripId, String type, double amount, String dateStr) {
        executor.execute(() -> {
            Expense expense = new Expense(tripId, type, amount, dateStr);
            appDao.insertExpense(expense);
            Log.d(TAG, "Expense added: " + amount);
        });
    }

    // 3. 获取某天的总消费
    public void getDailyTotal(int tripId, String dateStr, DataCallback<Double> callback) {
        executor.execute(() -> {
            Double total = appDao.getDailyTotalExpense(tripId, dateStr);
            mainHandler.post(() -> callback.onDataLoaded(total == null ? 0.0 : total));
        });
    }

    // 获取某天消费列表
    public void getDailyExpensesList(int tripId, String dateStr, DataCallback<List<Expense>> callback) {
        executor.execute(() -> {
            List<Expense> list = appDao.getDailyExpenses(tripId, dateStr);
            mainHandler.post(() -> callback.onDataLoaded(list));
        });
    }

    // 删除消费
    public void deleteExpense(Expense expense) {
        executor.execute(() -> {
            appDao.deleteExpense(expense); // 注意：你需要在 AppDao 里加一个 @Delete void deleteExpense(Expense expense);
        });
    }
}