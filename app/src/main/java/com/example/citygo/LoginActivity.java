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

        // Check if the user has already completed the initial setup
        if (isOnboardingComplete()) {
            navigateToMainActivity();
            return; // Navigate directly, do not load the login page layout
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Logic for the login button (currently simplified to directly enter preference selection)
        binding.loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // In a real application, username and password verification would be performed here
                navigateToPreferenceSelection();
            }
        });

        // Logic for the register button (also simplified to enter preference selection)
        binding.registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // In a real application, this would navigate to a separate registration page
                navigateToPreferenceSelection();
            }
        });
    }

    private boolean isOnboardingComplete() {
        SharedPreferences sharedPreferences = getSharedPreferences("CityGoPrefs", MODE_PRIVATE);
        // Check the flag, default is false
        return sharedPreferences.getBoolean("is_onboarding_complete", false);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish(); // Finish the current Activity to prevent the user from returning
    }

    private void navigateToPreferenceSelection() {
        Intent intent = new Intent(LoginActivity.this, PreferenceSelectionActivity.class);
        startActivity(intent);
    }
}