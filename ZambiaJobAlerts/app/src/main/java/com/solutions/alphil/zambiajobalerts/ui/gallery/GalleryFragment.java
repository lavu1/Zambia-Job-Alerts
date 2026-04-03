package com.solutions.alphil.zambiajobalerts.ui.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.solutions.alphil.zambiajobalerts.R;
import com.solutions.alphil.zambiajobalerts.databinding.FragmentGalleryBinding;

import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment {

    private FragmentGalleryBinding binding;
    private AboutUsAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupRecyclerView();
        loadAboutUs();
        AdView adView = binding.adViewAbout; // Assuming you add android:id="@+id/adView" to the XML
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        return root;
    }

    private void setupRecyclerView() {
        adapter = new AboutUsAdapter(new ArrayList<>());
        binding.rvAboutUs.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.rvAboutUs.setAdapter(adapter);
    }

    private void loadAboutUs() {
        List<AboutUsItem> aboutUsItems = new ArrayList<>();
        aboutUsItems.add(new AboutUsItem("Expert Engineers", "Our team provides professional career guidance, resume writing, and interview coaching to help you stand out."));
        aboutUsItems.add(new AboutUsItem("Experience Skills", "With years of experience in job placement and recruitment, we understand the job market and how to navigate it successfully."));
        aboutUsItems.add(new AboutUsItem("Low Cost", "We offer cost-effective job placement solutions for both job seekers and employers, ensuring everyone gets value for their money."));
        aboutUsItems.add(new AboutUsItem("Reliable & Verified Job Listings", "All job postings are carefully vetted to ensure authenticity, giving job seekers access to genuine opportunities."));
        aboutUsItems.add(new AboutUsItem("Trusted Work And Transparent", "We maintain a high level of transparency and integrity in all our services, ensuring a seamless job search and hiring experience."));
        aboutUsItems.add(new AboutUsItem("High Success Rate", "Many job seekers have successfully landed jobs through our platform, making us a trusted partner in career growth."));

        adapter.updateAboutUs(aboutUsItems);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // Simple data class for about us
    public static class AboutUsItem {
        private String title;
        private String description;

        public AboutUsItem(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
}

/*package com.solutions.alphil.zambiajobalerts.ui.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.solutions.alphil.zambiajobalerts.databinding.FragmentGalleryBinding;

public class GalleryFragment extends Fragment {

    private FragmentGalleryBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        GalleryViewModel galleryViewModel =
                new ViewModelProvider(this).get(GalleryViewModel.class);

        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textGallery;
        galleryViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
*/