package com.example.citygo.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppDao {

    // --- 用户相关 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User getUser(String email);

    // --- 行程相关 ---
    @Insert
    void insertTrip(Trip trip);

    @Query("SELECT * FROM trips WHERE userEmail = :email ORDER BY createdAt DESC")
    List<Trip> getUserTrips(String email);

    @Delete
    void deleteTrip(Trip trip);

    // 【新增】根据城市和开始日期查找 Trip，用于在 MapActivity 中锁定当前行程
    @Query("SELECT * FROM trips WHERE targetCity = :city AND startDate = :startDate LIMIT 1")
    Trip getTripByCityAndDate(String city, String startDate);

    // --- 【新增】记账相关 ---
    @Insert
    void insertExpense(Expense expense);

    // 获取某次行程、某一天的所有消费记录
    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND dateStr = :dateStr")
    List<Expense> getDailyExpenses(int tripId, String dateStr);

    // 获取某次行程、某一天的总花费
    @Query("SELECT SUM(amount) FROM expenses WHERE tripId = :tripId AND dateStr = :dateStr")
    Double getDailyTotalExpense(int tripId, String dateStr);

    @Delete
    void deleteExpense(Expense expense);
}