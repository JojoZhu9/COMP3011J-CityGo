package com.example.citygo.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// 【修改】entities 加入 Expense.class，version 改为 2
@Database(entities = {User.class, Trip.class, Expense.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AppDao appDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "citygo_local_db")
                            .fallbackToDestructiveMigration() // 【新增】允许版本升级时清空旧数据
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}