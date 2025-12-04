package com.example.citygo;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.citygo.databinding.ActivityProfileBinding;
import com.google.android.material.chip.Chip;

import java.util.Set;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private String[] DIETARY_OPTIONS;
    private DBService dbService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbService = new DBService(this);


        //---Dietary Options---
        DIETARY_OPTIONS = getResources().getStringArray(R.array.dietary_options);
        inflateDietaryChips();

        // Load saved prefs
        UserPrefs prefs = new UserPrefs(this);

        binding.inputName.setText(prefs.getName());
        binding.inputEmail.setText(prefs.getEmail());
        binding.inputCity.setText(prefs.getHomeCity());

        if (prefs.getDailyBudgetCents() > 0) {
            binding.inputBudget.setText(String.format("%.2f", prefs.getDailyBudgetCents() / 100.0));
        }

        restoreChipSelections(prefs);


        //--- CURRENCY DROPDOWN SETUP---
        ArrayAdapter<CharSequence> currencyAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.currency_list,
                android.R.layout.simple_list_item_1
        );
        binding.inputCurrency.setAdapter(currencyAdapter);

        // Restore saved currency
        String savedCurrency = prefs.getCurrencyDisplay();
        if (!TextUtils.isEmpty(savedCurrency)) {
            binding.inputCurrency.setText(savedCurrency, false);
        }

        // Save new currency when user selects one
        binding.inputCurrency.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            prefs.setCurrencyDisplay(selected);
        });

        // Open dropdown when clicked
        binding.inputCurrency.setOnClickListener(v -> binding.inputCurrency.showDropDown());


        //--- Save Button---
        binding.btnSave.setOnClickListener(v -> {
            saveProfile();
            Toast.makeText(this, getString(R.string.toast_profile_saved), Toast.LENGTH_SHORT).show();
            finish(); // return to previous screen
        });
    }

    // Inflate dietary chips dynamically
    private void inflateDietaryChips() {
        for (String label : DIETARY_OPTIONS) {
            Chip c = new Chip(this);
            c.setText(label);
            c.setCheckable(true);
            binding.chipGroupDietary.addView(c);
        }
    }

    // Restore dietary selections
    private void restoreChipSelections(UserPrefs prefs) {
        for (int i = 0; i < binding.chipGroupDietary.getChildCount(); i++) {
            Chip c = (Chip) binding.chipGroupDietary.getChildAt(i);
            c.setChecked(prefs.getDietary().contains(c.getText().toString()));
        }
    }

    // Save profile including name, email, budget, dietary, and currency
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

        // Save currency (ensures it persists even if dropdown wasn't tapped this session)
        String currency = safe(binding.inputCurrency.getText());
        if (!TextUtils.isEmpty(currency)) {
            prefs.setCurrencyDisplay(currency);
        }

        // Dietary
        Set<String> dietarySet = ChipUtils.selectedTexts(binding.chipGroupDietary);
        prefs.setDietary(dietarySet);

        // Save to DB
        String dietaryString = TextUtils.join(",", dietarySet);
        dbService.saveUserProfile(name, email, city, cents, dietaryString);
    }

    private static String safe(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}