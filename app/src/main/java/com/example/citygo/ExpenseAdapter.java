package com.example.citygo;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.citygo.database.Expense;
import com.example.citygo.databinding.ItemExpenseBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {

    public interface OnExpenseDeleteListener {
        void onDelete(Expense expense);
    }

    private List<Expense> expenses = new ArrayList<>();
    private OnExpenseDeleteListener listener;

    public void setOnExpenseDeleteListener(OnExpenseDeleteListener listener) {
        this.listener = listener;
    }

    public void setExpenses(List<Expense> expenses) {
        this.expenses = expenses;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExpenseBinding binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ExpenseViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = expenses.get(position);
        holder.binding.textCategory.setText(expense.type);
        holder.binding.textAmount.setText(String.format(Locale.US, "- %.2f", expense.amount));
        holder.binding.textDate.setText(expense.dateStr);

        if (!expense.type.isEmpty()) {
            holder.binding.textCategoryIcon.setText(expense.type.substring(0, 1).toUpperCase());
        }

        holder.binding.btnDeleteExpense.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(expense);
        });
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    static class ExpenseViewHolder extends RecyclerView.ViewHolder {
        ItemExpenseBinding binding;
        public ExpenseViewHolder(ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}