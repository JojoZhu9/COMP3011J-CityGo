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

    public void getTrip(String city, String startDate, DataCallback<Trip> callback) {
        executor.execute(() -> {
            Trip trip = appDao.getTripByCityAndDate(city, startDate);
            mainHandler.post(() -> callback.onDataLoaded(trip));
        });
    }

    public void addExpense(int tripId, String type, double amount, String dateStr) {
        executor.execute(() -> {
            Expense expense = new Expense(tripId, type, amount, dateStr);
            appDao.insertExpense(expense);
            Log.d(TAG, "Expense added: " + amount);
        });
    }

    public void getDailyTotal(int tripId, String dateStr, DataCallback<Double> callback) {
        executor.execute(() -> {
            Double total = appDao.getDailyTotalExpense(tripId, dateStr);
            mainHandler.post(() -> callback.onDataLoaded(total == null ? 0.0 : total));
        });
    }

    public void getDailyExpensesList(int tripId, String dateStr, DataCallback<List<Expense>> callback) {
        executor.execute(() -> {
            List<Expense> list = appDao.getDailyExpenses(tripId, dateStr);
            mainHandler.post(() -> callback.onDataLoaded(list));
        });
    }

    public void deleteExpense(Expense expense) {
        executor.execute(() -> {
            appDao.deleteExpense(expense);
        });
    }
}