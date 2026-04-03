package com.solutions.alphil.zambiajobalerts.ui.slideshow;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.solutions.alphil.zambiajobalerts.R;
import com.solutions.alphil.zambiajobalerts.ui.slideshow.SlideshowFragment;

import java.util.ArrayList;
import java.util.List;

public class ServicesAdapter extends RecyclerView.Adapter<ServicesAdapter.ServiceViewHolder> {

    private List<SlideshowFragment.ServiceItem> services = new ArrayList<>();

    public ServicesAdapter(List<SlideshowFragment.ServiceItem> services) {
        this.services = services;
    }

    public void updateServices(List<SlideshowFragment.ServiceItem> newServices) {
        this.services = newServices;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false);
        return new ServiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        SlideshowFragment.ServiceItem service = services.get(position);
        holder.tvTitle.setText(service.getTitle());
        holder.tvDescription.setText(service.getDescription());
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    static class ServiceViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription;

        public ServiceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
        }
    }
}