package com.example.citygo; // 请确保这是你自己的包名

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // 导入 Log 类
import android.view.View;
import android.widget.DatePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.citygo.databinding.ActivityCreateTripBinding;

import java.util.Calendar;

public class CreateTripActivity extends AppCompatActivity {

    // --- DEBUG: 定义一个统一的日志标签 ---
    private static final String TAG = "CreateTripDebug";

    private ActivityCreateTripBinding binding;
    private String selectedDate = null; // 初始值设为 null

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
                        // --- DEBUG: 确认日期已被成功设置 ---
                        Log.d(TAG, "Date selected and set: " + selectedDate);
                    }
                }, year, month, day);
        datePickerDialog.show();
    }

    private void generatePlan() {
        // --- DEBUG: 打印所有即将被检查的变量值 ---
        String city = binding.cityEditText.getText().toString().trim();
        String attractions = binding.attractionsEditText.getText().toString().trim();
        String daysStr = binding.daysEditText.getText().toString().trim();

        Log.d(TAG, "--- Checking values before generating plan ---");
        Log.d(TAG, "Checking City: '" + city + "'");
        Log.d(TAG, "Checking Attractions: '" + attractions + "'");
        Log.d(TAG, "Checking Days: '" + daysStr + "'");
        Log.d(TAG, "Checking Selected Date: '" + selectedDate + "'");

        // 数据校验
        if (TextUtils.isEmpty(city) || TextUtils.isEmpty(attractions) || TextUtils.isEmpty(daysStr) || selectedDate == null) {
            // --- DEBUG: 如果校验失败，打印一条错误日志 ---
            Log.e(TAG, "Validation FAILED. One or more fields are empty. Halting process.");
            Toast.makeText(this, "请填写所有信息，包括开始日期", Toast.LENGTH_SHORT).show();
            return;

        }

        // --- DEBUG: 如果校验成功，打印一条成功日志 ---
        Log.d(TAG, "Validation PASSED. Proceeding to MapActivity.");

        // 将所有数据打包到 Intent 中
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("EXTRA_CITY", city);
        intent.putExtra("EXTRA_ATTRACTIONS", attractions);
        intent.putExtra("EXTRA_DAYS", Integer.parseInt(daysStr));
        intent.putExtra("EXTRA_START_DATE", selectedDate);
        startActivity(intent);
    }
}