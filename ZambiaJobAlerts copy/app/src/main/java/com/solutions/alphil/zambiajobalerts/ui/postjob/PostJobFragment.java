package com.solutions.alphil.zambiajobalerts.ui.postjob;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.solutions.alphil.zambiajobalerts.databinding.FragmentPostJobBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PostJobFragment extends Fragment {

    private FragmentPostJobBinding binding;
    private PostJobViewModel viewModel;
    private InterstitialAd mInterstitialAd;
    private static final String AD_UNIT_ID = "ca-app-pub-2168080105757285/4046795138";

    private final Map<String, Integer> categoryMap = new HashMap<>();
    private final Map<String, Integer> typeMap = new HashMap<>();
    private final List<Integer> selectedCategoryIds = new ArrayList<>();
    private Integer selectedJobTypeId = 6; // Default Full Time

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPostJobBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PostJobViewModel.class);

        setupMappings();
        setupSpinners();
        loadInterstitialAd();

        binding.btnSubmitJob.setOnClickListener(v -> {
            String title = Objects.requireNonNull(binding.etJobTitle.getText()).toString();
            String company = Objects.requireNonNull(binding.etCompanyName.getText()).toString();
            String location = Objects.requireNonNull(binding.etLocation.getText()).toString();
            String description = Objects.requireNonNull(binding.etDescription.getText()).toString();
            String applicationLink = Objects.requireNonNull(binding.etApplicationLink.getText()).toString();

            if (title.isEmpty() || company.isEmpty() || location.isEmpty() || description.isEmpty() || applicationLink.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (mInterstitialAd != null) {
                mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        super.onAdDismissedFullScreenContent();
                        viewModel.postJob(title, company, location, description, applicationLink, selectedCategoryIds, selectedJobTypeId);
                        loadInterstitialAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        super.onAdFailedToShowFullScreenContent(adError);
                        viewModel.postJob(title, company, location, description, applicationLink, selectedCategoryIds, selectedJobTypeId);
                    }
                });
                mInterstitialAd.show(getActivity());
            } else {
                viewModel.postJob(title, company, location, description, applicationLink, selectedCategoryIds, selectedJobTypeId);
            }
        });

        viewModel.isPosting().observe(getViewLifecycleOwner(), isPosting -> {
            binding.progressBar.setVisibility(isPosting ? View.VISIBLE : View.GONE);
            binding.btnSubmitJob.setEnabled(!isPosting);
        });

        viewModel.getPostResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                Toast.makeText(getContext(), result, Toast.LENGTH_SHORT).show();
                if (result.contains("successfully")) {
                    clearFields();
                }
            }
        });
    }

    private void setupMappings() {
        // Categories
        categoryMap.put("Accountant", 11);
        categoryMap.put("Administrator", 12);
        categoryMap.put("Agriculture", 13);
        categoryMap.put("Banking/Finance", 14);
        categoryMap.put("Development", 15);
        categoryMap.put("Education", 16);
        categoryMap.put("Engineer/Construction", 17);
        categoryMap.put("Health", 18);
        categoryMap.put("Hospitality", 19);
        categoryMap.put("Human Resources", 20);
        categoryMap.put("IT/Telecoms", 21);
        categoryMap.put("Legal", 22);
        categoryMap.put("Manufacturing/FMCG", 23);
        categoryMap.put("Marketing/PR", 24);
        categoryMap.put("Public Sector", 26);
        categoryMap.put("Retail/Sales", 27);
        categoryMap.put("Logistics/Transport", 28);
        categoryMap.put("Other", 25);

        // Job Types
        typeMap.put("Full Time", 6);
        typeMap.put("Part Time", 7);
        typeMap.put("Temporary", 8);
        typeMap.put("Freelance", 9);
        typeMap.put("Internship", 10);
        typeMap.put("Consultancy", 30);
        typeMap.put("Contract", 31);
        typeMap.put("Tender", 32);
    }

    private void setupSpinners() {
        // Multi-select for categories
        String[] categories = categoryMap.keySet().toArray(new String[0]);
        boolean[] checkedCategories = new boolean[categories.length];

        binding.spinnerCategory.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Select Categories")
                    .setMultiChoiceItems(categories, checkedCategories, (dialog, which, isChecked) -> {
                        checkedCategories[which] = isChecked;
                    })
                    .setPositiveButton("OK", (dialog, which) -> {
                        selectedCategoryIds.clear();
                        StringBuilder selectedNames = new StringBuilder();
                        for (int i = 0; i < checkedCategories.length; i++) {
                            if (checkedCategories[i]) {
                                selectedCategoryIds.add(categoryMap.get(categories[i]));
                                if (selectedNames.length() > 0) selectedNames.append(", ");
                                selectedNames.append(categories[i]);
                            }
                        }
                        binding.spinnerCategory.setText(selectedNames.toString());
                    })
                    .show();
        });

        // Single select for job type
        String[] types = typeMap.keySet().toArray(new String[0]);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, types);
        binding.spinnerJobType.setAdapter(typeAdapter);
        binding.spinnerJobType.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = (String) parent.getItemAtPosition(position);
            selectedJobTypeId = typeMap.get(selectedType);
        });
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(requireContext(), AD_UNIT_ID, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                    }
                });
    }

    private void clearFields() {
        binding.etJobTitle.setText("");
        binding.etCompanyName.setText("");
        binding.etLocation.setText("");
        binding.etDescription.setText("");
        binding.etApplicationLink.setText("");
        binding.spinnerCategory.setText("");
        binding.spinnerJobType.setText("");
        selectedCategoryIds.clear();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}