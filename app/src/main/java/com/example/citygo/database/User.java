package com.example.citygo.database; // 建议放在 database 包下

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String email;
    public String name;
    public String homeCity;
    public int budgetCents;
    public String dietaryPrefs;

    // 构造函数
    public User(String email, String name, String homeCity, int budgetCents, String dietaryPrefs) {
        this.email = email;
        this.name = name;
        this.homeCity = homeCity;
        this.budgetCents = budgetCents;
        this.dietaryPrefs = dietaryPrefs;
    }
}