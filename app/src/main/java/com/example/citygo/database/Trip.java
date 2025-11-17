package com.example.citygo.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trips")
public class Trip {
    @PrimaryKey(autoGenerate = true)
    public int tripId;

    public String userEmail;
    public String targetCity;
    public String attractions; // 存为字符串，例如 "A,B,C"
    public int totalDays;
    public String startDate;
    public long createdAt; // 时间戳

    public Trip(String userEmail, String targetCity, String attractions, int totalDays, String startDate) {
        this.userEmail = userEmail;
        this.targetCity = targetCity;
        this.attractions = attractions;
        this.totalDays = totalDays;
        this.startDate = startDate;
        this.createdAt = System.currentTimeMillis();
    }
}