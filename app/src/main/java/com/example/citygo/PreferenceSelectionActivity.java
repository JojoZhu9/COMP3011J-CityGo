package com.example.citygo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.example.citygo.databinding.ActivityPreferenceSelectionBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PreferenceSelectionActivity extends AppCompatActivity {

    private ActivityPreferenceSelectionBinding binding;

    private final String[] PREFERENCE_TAGS = {
            "美食探店", "咖啡文化", "历史古迹", "艺术画廊", "购物中心",
            "公园绿地", "现场音乐", "建筑欣赏", "本地市集", "网红打卡",
            "亲子活动", "静谧小巷"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPreferenceSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        populateChipGroup();

        binding.finishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserPreferencesAndNavigate();
            }
        });
    }

    private void populateChipGroup() {
        for (String tagName : PREFERENCE_TAGS) {
            Chip chip = new Chip(this);
            chip.setText(tagName);
            chip.setCheckable(true);
            binding.preferenceChipGroup.addView(chip);
        }
    }

    private void saveUserPreferencesAndNavigate() {
        List<Integer> checkedChipIds = binding.preferenceChipGroup.getCheckedChipIds();

        if (checkedChipIds.isEmpty()) {
            Toast.makeText(this, "请至少选择一个你感兴趣的标签", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> selectedPreferences = new ArrayList<>();
        for (Integer id : checkedChipIds) {
            Chip chip = binding.preferenceChipGroup.findViewById(id);
            if (chip != null) {
                selectedPreferences.add(chip.getText().toString());
            }
        }

        SharedPreferences sharedPreferences = getSharedPreferences("CityGoPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet("user_preferences", new HashSet<>(selectedPreferences));
        editor.putBoolean("is_onboarding_complete", true);
        editor.apply();

        Toast.makeText(this, "偏好已保存，开始你的城市之旅吧！", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(PreferenceSelectionActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}