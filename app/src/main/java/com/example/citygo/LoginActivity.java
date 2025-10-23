package com.example.citygo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.citygo.databinding.ActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查用户是否已经完成过初次设置
        if (isOnboardingComplete()) {
            navigateToMainActivity();
            return; // 直接跳转，不加载登录页布局
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 登录按钮的逻辑（目前简化为直接进入偏好设置）
        binding.loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 在真实应用中，这里会进行用户名和密码的验证
                navigateToPreferenceSelection();
            }
        });

        // 注册按钮的逻辑（目前也简化为进入偏好设置）
        binding.registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 在真实应用中，这里会跳转到单独的注册页面
                navigateToPreferenceSelection();
            }
        });
    }

    private boolean isOnboardingComplete() {
        SharedPreferences sharedPreferences = getSharedPreferences("CityGoPrefs", MODE_PRIVATE);
        // 检查标志位，默认为 false
        return sharedPreferences.getBoolean("is_onboarding_complete", false);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish(); // 结束当前 Activity，防止用户返回
    }

    private void navigateToPreferenceSelection() {
        Intent intent = new Intent(LoginActivity.this, PreferenceSelectionActivity.class);
        startActivity(intent);
    }
}