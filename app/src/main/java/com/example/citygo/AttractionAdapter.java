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

    // 1. 定义一个更全面的监听器接口
    public interface OnAttractionActionsListener {
        void onSearchNearbyClick(String attractionName);
        void onAttractionRemoved(int position);
        // 拖拽结束后，通知 Activity
        void onAttractionsReordered();
    }

    // 定义一个接口，让 Activity 可以命令 Adapter 开始拖拽
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

    // 拖拽时调用的方法
    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(attractionNames, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    // 删除时调用的方法
    public void onItemRemove(int position) {
        attractionNames.remove(position);
        notifyItemRemoved(position);
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

        // “搜周边”按钮的点击事件
        holder.binding.searchNearbyButton.setOnClickListener(v -> {
            if (actionsListener != null) {
                actionsListener.onSearchNearbyClick(attractionName);
            }
        });

        // “操作”图标的点击事件 (用于删除)
        holder.binding.dragHandle.setOnClickListener(v -> {
            if (actionsListener != null) {
                // 直接通知 Activity 显示删除确认
                actionsListener.onAttractionRemoved(holder.getAdapterPosition());
            }
        });

        // “操作”图标的触摸事件 (用于拖拽)
        holder.binding.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (dragListener != null) {
                    // 当用户按下图标时，请求 Activity 开始拖拽
                     dragListener.requestDrag(holder);
                }
            }
            return false;
        });
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