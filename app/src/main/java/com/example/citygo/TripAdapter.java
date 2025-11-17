package com.example.citygo;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.citygo.database.Trip;
import com.example.citygo.databinding.ItemTripBinding;
import java.util.ArrayList;
import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    public interface OnTripActionListener {
        void onTripClick(Trip trip);
        void onTripDelete(Trip trip);
    }

    private List<Trip> trips = new ArrayList<>();
    private OnTripActionListener listener;

    public void setOnTripActionListener(OnTripActionListener listener) {
        this.listener = listener;
    }

    public void setTrips(List<Trip> trips) {
        this.trips = trips;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTripBinding binding = ItemTripBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new TripViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);
        holder.binding.textCity.setText(trip.targetCity);
        holder.binding.textDate.setText(trip.startDate + " (" + trip.totalDays + " Days)");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTripClick(trip);
        });

        holder.binding.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onTripDelete(trip);
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        ItemTripBinding binding;
        public TripViewHolder(ItemTripBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}