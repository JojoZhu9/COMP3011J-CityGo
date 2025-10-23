package com.example.citygo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.citygo.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 设置浮动操作按钮的点击监听器
        binding.fabAddTrip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 点击按钮后，启动 CreateTripActivity
                Intent intent = new Intent(MainActivity.this, CreateTripActivity.class);
                startActivity(intent);
            }
        });
    }
}