package com.example.citygo;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.citygo.databinding.ActivityMapBinding;

import java.util.Locale;

/**
 * Handles all budget / expense UI logic for MapActivity:
 * - updates daily budget progress bar
 * - shows expense list dialog
 * - shows add-expense dialog
 */
public class TripBudgetController {

    private final AppCompatActivity activity;
    private final ActivityMapBinding binding;
    private final DBService dbService;

    private int tripId = -1;
    private double dailyBudget;

    // NEW: currency string as shown in Profile, e.g. "USD ($)"
    private String currencyDisplay;

    public TripBudgetController(AppCompatActivity activity,
                                ActivityMapBinding binding,
                                DBService dbService,
                                double dailyBudget,
                                String currencyDisplay) {
        this.activity = activity;
        this.binding = binding;
        this.dbService = dbService;
        this.dailyBudget = dailyBudget;

        // Fallback if nothing is provided
        if (TextUtils.isEmpty(currencyDisplay)) {
            this.currencyDisplay = "USD ($)";
        } else {
            this.currencyDisplay = currencyDisplay;
        }
    }

    public void setTripId(int tripId) {
        this.tripId = tripId;
    }

    public void setDailyBudget(double dailyBudget) {
        this.dailyBudget = dailyBudget;
    }

    public void setCurrencyDisplay(String currencyDisplay) {
        if (!TextUtils.isEmpty(currencyDisplay)) {
            this.currencyDisplay = currencyDisplay;
        }
    }

    /**
     * Update the budget UI for a given date string (yyyy-MM-dd).
     * Caller is responsible for passing the correct date for the current day.
     */
    public void updateBudgetUI(String dateStr) {
        if (tripId == -1) return;

        // Tap on progress bar opens expense list dialog for that date
        binding.progressBarBudget.setOnClickListener(v -> showExpenseListDialog(dateStr));

        dbService.getDailyTotal(tripId, dateStr, total ->
                activity.runOnUiThread(() -> {
                    // Show: "<CURRENCY> spent / budget"
                    // e.g. "USD ($) 35.00 / 100.00"
                    binding.textExpense.setText(
                            String.format(
                                    Locale.US,
                                    "%s %.2f / %.2f",
                                    currencyDisplay,
                                    total,
                                    dailyBudget
                            )
                    );

                    int progress = (dailyBudget > 0)
                            ? Math.min((int) ((total / dailyBudget) * 100), 100)
                            : 0;
                    binding.progressBarBudget.setProgress(progress);

                    if (total > dailyBudget) {
                        binding.progressBarBudget.getProgressDrawable().setColorFilter(
                                android.graphics.Color.RED,
                                android.graphics.PorterDuff.Mode.SRC_IN
                        );
                    } else {
                        binding.progressBarBudget.getProgressDrawable().clearColorFilter();
                    }
                })
        );
    }

    /**
     * Show the list of expenses for a given date.
     */
    public void showExpenseListDialog(String dateStr) {
        if (tripId == -1) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Expenses for " + dateStr);

        RecyclerView rv = new RecyclerView(activity);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        ExpenseAdapter adapter = new ExpenseAdapter();
        rv.setAdapter(adapter);
        rv.setPadding(32, 32, 32, 32);
        builder.setView(rv);
        builder.setPositiveButton("Close", null);
        builder.show();

        dbService.getDailyExpensesList(tripId, dateStr, list -> {
            if (list.isEmpty()) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "No expenses yet.", Toast.LENGTH_SHORT).show()
                );
            }
            adapter.setExpenses(list);
        });

        adapter.setOnExpenseDeleteListener(expense -> {
            dbService.deleteExpense(expense);
            dbService.getDailyExpensesList(tripId, dateStr, adapter::setExpenses);
            // refresh budget after delete
            updateBudgetUI(dateStr);
        });
    }

    /**
     * Show "add expense" dialog for the given date (typically current day).
     */
    public void showAddExpenseDialog(String dateStr) {
        if (tripId == -1) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_add_expense, null);
        builder.setView(view);

        // --- Amount + category views ---
        com.google.android.material.textfield.TextInputEditText inputAmount =
                view.findViewById(R.id.inputAmount);
        com.google.android.material.chip.ChipGroup chipGroup =
                view.findViewById(R.id.chipGroupCategory);

        // --- NEW: currency label under the amount field ---
        android.widget.TextView textCurrencyUnit = view.findViewById(R.id.textCurrencyUnit);

        // Read display currency from prefs (e.g. "USD ($)", "EUR (€)")
        UserPrefs prefs = new UserPrefs(activity);
        String currencyDisplay = prefs.getCurrencyDisplay();
        if (android.text.TextUtils.isEmpty(currencyDisplay)) {
            // Fallback if user never set it – you can change this default if you like
            currencyDisplay = "CNY (¥)";
        }
        textCurrencyUnit.setText(currencyDisplay);

        // --- Buttons ---
        builder.setPositiveButton("Save", (dialog, which) -> {
            String amountStr = inputAmount.getText() != null
                    ? inputAmount.getText().toString()
                    : "";
            if (amountStr.isEmpty()) return;

            String category = "Other";
            if (chipGroup.getCheckedChipId() != -1) {
                category = ((com.google.android.material.chip.Chip)
                        view.findViewById(chipGroup.getCheckedChipId()))
                        .getText()
                        .toString();
            }

            double amount = Double.parseDouble(amountStr);
            dbService.addExpense(tripId, category, amount, dateStr);
            updateBudgetUI(dateStr);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

}
