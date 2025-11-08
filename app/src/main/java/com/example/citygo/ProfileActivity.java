package com.example.citygo;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.citygo.databinding.ActivityProfileBinding;
import com.google.android.material.chip.Chip;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private String[] DIETARY_OPTIONS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        DIETARY_OPTIONS = getResources().getStringArray(R.array.dietary_options);
        inflateDietaryChips();

        // Prefill
        UserPrefs prefs = new UserPrefs(this);
        binding.inputName.setText(prefs.getName());
        binding.inputEmail.setText(prefs.getEmail());
        binding.inputCity.setText(prefs.getHomeCity());
        if (prefs.getDailyBudgetCents() > 0) {
            binding.inputBudget.setText(String.format("%.2f", prefs.getDailyBudgetCents() / 100.0));
        }
        restoreChipSelections(prefs);

        binding.btnSave.setOnClickListener(v -> {
            saveProfile();
            Toast.makeText(this, getString(R.string.toast_profile_saved), Toast.LENGTH_SHORT).show();
            finish(); // return to MainActivity
        });
    }

    private void inflateDietaryChips() {
        for (String label : DIETARY_OPTIONS) {
            Chip c = new Chip(this);
            c.setText(label);
            c.setCheckable(true);
            binding.chipGroupDietary.addView(c);
        }
    }

    private void restoreChipSelections(UserPrefs prefs) {
        for (int i = 0; i < binding.chipGroupDietary.getChildCount(); i++) {
            Chip c = (Chip) binding.chipGroupDietary.getChildAt(i);
            c.setChecked(prefs.getDietary().contains(c.getText().toString()));
        }
    }

    private void saveProfile() {
        String name = safe(binding.inputName.getText());
        String email = safe(binding.inputEmail.getText());
        String city = safe(binding.inputCity.getText());
        String budgetStr = safe(binding.inputBudget.getText());

        int cents = 0;
        if (!TextUtils.isEmpty(budgetStr)) {
            try { cents = (int) Math.round(Double.parseDouble(budgetStr) * 100); }
            catch (Exception ignored) { cents = 0; }
        }

        UserPrefs prefs = new UserPrefs(this);
        prefs.setName(name);
        prefs.setEmail(email);
        prefs.setHomeCity(city);
        prefs.setDailyBudgetCents(Math.max(0, cents));
        prefs.setDietary(ChipUtils.selectedTexts(binding.chipGroupDietary));
    }

    private static String safe(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
