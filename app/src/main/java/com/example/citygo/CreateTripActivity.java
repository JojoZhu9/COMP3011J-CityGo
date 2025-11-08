package com.example.citygo;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // Import Log class
import android.view.View;
import android.widget.DatePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.citygo.databinding.ActivityCreateTripBinding;

import java.util.Calendar;

public class CreateTripActivity extends AppCompatActivity {

    // --- DEBUG: Define a unified log tag ---
    private static final String TAG = "CreateTripDebug";

    private ActivityCreateTripBinding binding;
    private String selectedDate = null; // Set initial value to null

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTripBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.startDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        binding.generatePlanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generatePlan();
            }
        });
    }

    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        selectedDate = year + "-" + (monthOfYear + 1) + "-" + dayOfMonth;
                        binding.startDateButton.setText(selectedDate);
                        // --- DEBUG: Confirm that the date has been successfully set ---
                        Log.d(TAG, "Date selected and set: " + selectedDate);
                    }
                }, year, month, day);
        datePickerDialog.show();
    }

    private void generatePlan() {
        // --- DEBUG: Print all variable values that are about to be checked ---
        String city = binding.cityEditText.getText().toString().trim();
        String attractions = binding.attractionsEditText.getText().toString().trim();
        String daysStr = binding.daysEditText.getText().toString().trim();

        Log.d(TAG, "--- Checking values before generating plan ---");
        Log.d(TAG, "Checking City: '" + city + "'");
        Log.d(TAG, "Checking Attractions: '" + attractions + "'");
        Log.d(TAG, "Checking Days: '" + daysStr + "'");
        Log.d(TAG, "Checking Selected Date: '" + selectedDate + "'");

        // Data validation
        if (TextUtils.isEmpty(city) || TextUtils.isEmpty(attractions) || TextUtils.isEmpty(daysStr) || selectedDate == null) {
            // --- DEBUG: If validation fails, print an error log ---
            Log.e(TAG, "Validation FAILED. One or more fields are empty. Halting process.");
            Toast.makeText(this, getString(R.string.toast_fill_all_info), Toast.LENGTH_SHORT).show();
            return;

        }

        // --- DEBUG: If validation passes, print a success log ---
        Log.d(TAG, "Validation PASSED. Proceeding to MapActivity.");

        // Pack all data into the Intent
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("EXTRA_CITY", city);
        intent.putExtra("EXTRA_ATTRACTIONS", attractions);
        intent.putExtra("EXTRA_DAYS", Integer.parseInt(daysStr));
        intent.putExtra("EXTRA_START_DATE", selectedDate);
        startActivity(intent);
    }
}