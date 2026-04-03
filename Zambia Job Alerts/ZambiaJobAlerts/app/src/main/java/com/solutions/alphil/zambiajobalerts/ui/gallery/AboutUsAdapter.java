package com.solutions.alphil.zambiajobalerts.ui.gallery;

//package com.solutions.alphil.zambiajobalerts.ui.gallery.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.solutions.alphil.zambiajobalerts.R;

import java.util.ArrayList;
import java.util.List;

public class AboutUsAdapter extends RecyclerView.Adapter<AboutUsAdapter.AboutUsViewHolder> {

    private List<GalleryFragment.AboutUsItem> aboutUsItems = new ArrayList<>();

    public AboutUsAdapter(List<GalleryFragment.AboutUsItem> aboutUsItems) {
        this.aboutUsItems = aboutUsItems;
    }

    public void updateAboutUs(List<GalleryFragment.AboutUsItem> newItems) {
        this.aboutUsItems = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AboutUsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_about_us, parent, false);
        return new AboutUsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AboutUsViewHolder holder, int position) {
        GalleryFragment.AboutUsItem item = aboutUsItems.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvDescription.setText(item.getDescription());
    }

    @Override
    public int getItemCount() {
        return aboutUsItems.size();
    }

    static class AboutUsViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription;

        public AboutUsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
        }
    }
}
