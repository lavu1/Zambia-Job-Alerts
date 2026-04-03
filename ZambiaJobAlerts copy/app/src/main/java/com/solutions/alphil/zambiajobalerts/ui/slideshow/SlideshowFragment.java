/*package com.solutions.alphil.zambiajobalerts.ui.slideshow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.solutions.alphil.zambiajobalerts.databinding.FragmentSlideshowBinding;

public class SlideshowFragment extends Fragment {

    private FragmentSlideshowBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        SlideshowViewModel slideshowViewModel =
                new ViewModelProvider(this).get(SlideshowViewModel.class);

        binding = FragmentSlideshowBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textSlideshow;
        slideshowViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
*/

package com.solutions.alphil.zambiajobalerts.ui.slideshow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.solutions.alphil.zambiajobalerts.R;
import com.solutions.alphil.zambiajobalerts.databinding.FragmentSlideshowBinding;

import java.util.ArrayList;
import java.util.List;

public class SlideshowFragment extends Fragment {

    private FragmentSlideshowBinding binding;
    private ServicesAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSlideshowBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupRecyclerView();
        loadServices();
        AdView adView = binding.adViewServices; // Assuming you add android:id="@+id/adView" to the XML
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        return root;
    }

    private void setupRecyclerView() {
        adapter = new ServicesAdapter(new ArrayList<>());
        binding.rvServices.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvServices.setAdapter(adapter);
    }

    private void loadServices() {
        List<ServiceItem> services = new ArrayList<>();
        services.add(new ServiceItem("Job Posting & Recruitment", "Employers can post job vacancies and find the best candidates through our platform"));
        services.add(new ServiceItem("Resume Writing & Review", "We help job seekers create professional resumes that stand out to potential employers"));
        services.add(new ServiceItem("Application Letter Writing", "Our team assists in crafting compelling and personalized job application letters"));
        services.add(new ServiceItem("Career Guidance & Coaching", "We provide expert career advice, interview tips, and industry insights to help job seekers succeed."));
        services.add(new ServiceItem("Internship & Graduate Programs", "We connect fresh graduates with internship opportunities to gain valuable work experience."));
        services.add(new ServiceItem("Freelance & Remote Work Opportunities", "We help professionals find remote or freelance jobs suited to their skills and experience."));

        adapter.updateServices(services);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // Simple data class for services
    public static class ServiceItem {
        private String title;
        private String description;

        public ServiceItem(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
}