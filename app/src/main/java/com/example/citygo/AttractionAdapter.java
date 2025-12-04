package com.example.citygo;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.citygo.databinding.ItemAttractionBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AttractionAdapter extends RecyclerView.Adapter<AttractionAdapter.AttractionViewHolder> {

    // Define an interface to allow the Activity to command the Adapter to start dragging
    public interface StartDragListener {
        void requestDrag(RecyclerView.ViewHolder viewHolder);
    }

    private List<String> attractionNames = new ArrayList<>();
    private OnAttractionActionsListener actionsListener;
    private StartDragListener dragListener;

    public void setOnAttractionActionsListener(OnAttractionActionsListener listener) {
        this.actionsListener = listener;
    }

    public void setStartDragListener(StartDragListener listener) {
        this.dragListener = listener;
    }

    public List<String> getAttractionNames() {
        return this.attractionNames;
    }

    public void updateData(List<String> newAttractionNames) {
        this.attractionNames.clear();
        if (newAttractionNames != null) {
            this.attractionNames.addAll(newAttractionNames);
        }
        notifyDataSetChanged();
    }

    // Method called when an item is moved
    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(attractionNames, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public AttractionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAttractionBinding binding = ItemAttractionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new AttractionViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull AttractionViewHolder holder, int position) {
        String attractionName = attractionNames.get(position);
        holder.bind(attractionName);

        // Click event for the "Search Nearby" button
        holder.binding.searchNearbyButton.setOnClickListener(v -> {
            if (actionsListener != null) {
                actionsListener.onSearchNearbyClick(attractionName);
            }
        });

        // Click event for the "handle" icon (for deletion)
        holder.binding.dragHandle.setOnClickListener(v -> {
            if (actionsListener != null) {
                // Directly notify the Activity to show deletion confirmation
                actionsListener.onAttractionRemoved(holder.getAdapterPosition());
            }
        });

        // Touch event for the "handle" icon (for dragging)
        holder.binding.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (dragListener != null) {
                    // When the user presses the icon, request the Activity to start dragging
                    dragListener.requestDrag(holder);
                }
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            if (actionsListener != null) {
                actionsListener.onAttractionClick(holder.getAdapterPosition());
            }
        });
    }

    public interface OnAttractionActionsListener {
        void onSearchNearbyClick(String attractionName);
        void onAttractionRemoved(int position);
        void onAttractionsReordered();

        // 【新增】点击条目本身的接口
        void onAttractionClick(int position);
    }

    @Override
    public int getItemCount() {
        return attractionNames.size();
    }

    static class AttractionViewHolder extends RecyclerView.ViewHolder {
        private final ItemAttractionBinding binding;

        public AttractionViewHolder(ItemAttractionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(String attractionName) {
            binding.attractionNameTextView.setText(attractionName);
        }
    }
}