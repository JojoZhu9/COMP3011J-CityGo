package com.example.citygo.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

// 定义外键：如果 Trip 被删除了，对应的 Expense 也会自动删除 (CASCADE)
@Entity(tableName = "expenses",
        foreignKeys = @ForeignKey(entity = Trip.class,
                parentColumns = "tripId",
                childColumns = "tripId",
                onDelete = CASCADE),
        indices = {@Index("tripId")}) // 加索引提升查询速度
public class Expense {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int tripId;      // 关联的行程 ID
    public String type;     // 消费类型：餐饮、交通、购物、门票、其他
    public double amount;   // 金额
    public String dateStr;  // 日期字符串 (yyyy-MM-dd)，用于区分是哪一天的花费

    public Expense(int tripId, String type, double amount, String dateStr) {
        this.tripId = tripId;
        this.type = type;
        this.amount = amount;
        this.dateStr = dateStr;
    }
}