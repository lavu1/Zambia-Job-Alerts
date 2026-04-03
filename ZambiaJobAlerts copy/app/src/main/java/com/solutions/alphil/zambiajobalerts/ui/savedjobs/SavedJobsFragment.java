package com.solutions.alphil.zambiajobalerts.ui.savedjobs;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.material.snackbar.Snackbar;
import com.solutions.alphil.zambiajobalerts.classes.Job;
import com.solutions.alphil.zambiajobalerts.classes.JobDetailsBottomSheet;
import com.solutions.alphil.zambiajobalerts.classes.JobsAdapter;
import com.solutions.alphil.zambiajobalerts.databinding.FragmentSavedJobsBinding;

import java.util.ArrayList;
import java.util.List;

public class SavedJobsFragment extends Fragment {

    private FragmentSavedJobsBinding binding;
    private SavedJobsViewModel viewModel;
    private JobsAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSavedJobsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SavedJobsViewModel.class);

        setupRecyclerView();
        setupAd();
        setupObservers();

        // Start local notification trigger
        scheduleLocalReminder();
    }

    private void setupRecyclerView() {
        adapter = new JobsAdapter(new ArrayList<>(), job -> {
            JobDetailsBottomSheet detailsSheet = JobDetailsBottomSheet.newInstance(job.getId());
            detailsSheet.show(getParentFragmentManager(), "JobDetails");
        });
        binding.rvSavedJobs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvSavedJobs.setAdapter(adapter);
    }

    private void setupAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        binding.adViewSaved.loadAd(adRequest);
    }

    private void setupObservers() {
        viewModel.getSavedJobs().observe(getViewLifecycleOwner(), jobs -> {
            if (jobs != null && !jobs.isEmpty()) {
                List<Object> displayList = new ArrayList<>(jobs);
                adapter.updateDisplayList(displayList);
                binding.rvSavedJobs.setVisibility(View.VISIBLE);
                binding.layoutEmpty.setVisibility(View.GONE);
            } else {
                binding.rvSavedJobs.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void scheduleLocalReminder() {
        handler.postDelayed(() -> {
            if (isAdded() && viewModel.getSavedJobs().getValue() != null && !viewModel.getSavedJobs().getValue().isEmpty()) {
                Snackbar.make(binding.getRoot(), "Check your saved jobs! New opportunities are arriving.", Snackbar.LENGTH_LONG)
                        .setAction("View", v -> {
                            // Already here or navigate here
                        })
                        .show();
            }
            scheduleLocalReminder(); // Re-schedule
        }, 60000 * 30); // Every 30 minutes for demo, can be longer
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }
}