package com.example.citygo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.citygo.database.Trip;
import com.example.citygo.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DBService dbService;
    private TripAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbService = new DBService(this);

        setupRecyclerView();

        binding.btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });

        binding.fabAddTrip.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, CreateTripActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrips(); // 每次回到页面都刷新列表
    }

    private void setupRecyclerView() {
        adapter = new TripAdapter();
        binding.recyclerViewTrips.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewTrips.setAdapter(adapter);

        adapter.setOnTripActionListener(new TripAdapter.OnTripActionListener() {
            @Override
            public void onTripClick(Trip trip) {
                // 点击进入 MapActivity，传入保存的数据
                Intent intent = new Intent(MainActivity.this, MapActivity.class);
                intent.putExtra("EXTRA_CITY", trip.targetCity);
                intent.putExtra("EXTRA_ATTRACTIONS", trip.attractions);
                intent.putExtra("EXTRA_DAYS", trip.totalDays);
                intent.putExtra("EXTRA_START_DATE", trip.startDate);
                startActivity(intent);
            }

            @Override
            public void onTripDelete(Trip trip) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Trip")
                        .setMessage("Are you sure you want to delete the trip to " + trip.targetCity + "?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            dbService.deleteTrip(trip);
                            loadTrips(); // 刷新列表
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void loadTrips() {
        UserPrefs prefs = new UserPrefs(this);
        String email = prefs.getEmail();
        if (email.isEmpty()) email = "anonymous@citygo.com";

        dbService.getUserTrips(email, trips -> {
            if (trips.isEmpty()) {
                binding.recyclerViewTrips.setVisibility(View.GONE);
                binding.emptyView.setVisibility(View.VISIBLE);
            } else {
                binding.recyclerViewTrips.setVisibility(View.VISIBLE);
                binding.emptyView.setVisibility(View.GONE);
                adapter.setTrips(trips);
            }
        });
    }
}