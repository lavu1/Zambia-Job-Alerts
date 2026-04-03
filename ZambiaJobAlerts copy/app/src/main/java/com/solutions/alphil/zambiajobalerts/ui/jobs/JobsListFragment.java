package com.solutions.alphil.zambiajobalerts.ui.jobs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.google.android.material.snackbar.Snackbar;
import com.solutions.alphil.zambiajobalerts.R;
import com.solutions.alphil.zambiajobalerts.classes.Job;
import com.solutions.alphil.zambiajobalerts.classes.JobDetailsBottomSheet;
import com.solutions.alphil.zambiajobalerts.classes.JobsAdapter;
import com.solutions.alphil.zambiajobalerts.databinding.FragmentJobsListBinding;
import com.solutions.alphil.zambiajobalerts.ui.rewards.EmailJobAlertsFragment;
import com.solutions.alphil.zambiajobalerts.ui.services.ServicesFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JobsListFragment extends Fragment {

    private FragmentJobsListBinding binding;
    private JobsAdapter adapter;
    private JobsViewModel viewModel;
    private SharedPreferences prefs;
    private RewardedInterstitialAd rewardedInterstitialAd;
    private static final String AD_UNIT_ID = "ca-app-pub-2168080105757285/9306188221";
    private List<NativeAd> nativeAdPool = new ArrayList<>();
    private AdLoader adLoader;
    private boolean isDetailsShowing = false;
    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-2168080105757285/5207161115";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentJobsListBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupViewModel();
        prefs = requireActivity().getSharedPreferences("ad_prefs", Context.MODE_PRIVATE);
        initializeNativeAds();
        initViews();
        setupRecyclerView();
        setupObservers();
/*
        // Initialize ViewModel FIRST
        setupViewModel();

        // Then setup other components
        prefs = requireActivity().getSharedPreferences("ad_prefs", Context.MODE_PRIVATE);
        initializeNativeAds();
        initViews();
        maybeShowRandomSnackbar(root);
        setupRecyclerView();  // This should come AFTER ViewModel setup
        setupObservers();*/
        //MobileAds.initialize(requireContext());
        //new RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("17D2E7269CB0E97CE8414829BEF87A53"));

        AdView adViewsearch = binding.adViewsearch;
        AdRequest adRequestsearch = new AdRequest.Builder().build();
        adViewsearch.loadAd(adRequestsearch);

        AdView adView = binding.adView;
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        // Handle job_id from intent
        if (getActivity() != null) {
            Intent intent = getActivity().getIntent();
            int jobId = intent.getIntExtra("job_id", -1);
            if (jobId > 0) {
                intent.putExtra("job_id", -1);
                JobDetailsBottomSheet detailsSheet = JobDetailsBottomSheet.newInstance(jobId);
                detailsSheet.show(getParentFragmentManager(), "JobDetails");
            }
        }
        if (getArguments() != null) {
            int openJobId = getArguments().getInt("open_job_id", -1);
            if (openJobId > 0) {
                JobDetailsBottomSheet detailsSheet = JobDetailsBottomSheet.newInstance(openJobId);
                detailsSheet.show(getParentFragmentManager(), "JobDetails");
                // Clear the argument to avoid reopening
                getArguments().remove("open_job_id");
            }
        }

        return root;
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Now it's safe – view is attached
        maybeShowRandomSnackbar();   // ← pass view or binding.root
    }
    // 🎯 Random motivational Snackbar pop-ups
 /*
    private void maybeShowRandomSnackbar(View root) {
        // 20% chance to show
//
//        if (Math.random() < 0.2) {
//            String[] messages = {
//                    "💡 Always tailor your CV to the specific job you apply for.",
//                    "🎬 Watch ads as a subscriber to keep your access active!",
//                    "🏆 Redeem your points and unlock special rewards.",
//                    "⏳ Your subscription is valid for only a day — renew to stay premium!",
//                    "🚀 Keep applying — your next opportunity is around the corner!"
//            };
//
//            int index = (int) (Math.random() * messages.length);
//            Snackbar.make(root, messages[index], Snackbar.LENGTH_LONG)
//                    .setAction("Okay", v -> { openFragment(new ServicesFragment()); })
//                    .show();
//        }

        if (Math.random() < 0.2) {
            String[] messages = {
                    "💡 Always tailor your CV to the specific job you apply for.",
                    "🎬 Watch ads as a subscriber to keep your access active!",
                    "🏆 Redeem your points and unlock special rewards.",
                    "⏳ Your subscription is valid for only a day — renew to stay premium!",
                    "🚀 Keep applying — your next opportunity is around the corner!"
            };

            int index = (int) (Math.random() * messages.length);

            Snackbar.make(root, messages[index], Snackbar.LENGTH_LONG)
                    .setAction("Okay", v -> openFragment(new ServicesFragment()))
                    .show();
        }
    }*/
    private void maybeShowRandomSnackbar() {
        if (Math.random() < 0.2) {
            String[] messages = {
                    "💡 Always tailor your CV to the specific job you apply for.",
                    "🎬 Watch ads as a subscriber to keep your access active!",
                    "🏆 Redeem your points and unlock special rewards.",
                    "⏳ Your subscription is valid for only a day — renew to stay premium!",
                    "🚀 Keep applying — your next opportunity is around the corner!"
            };
            int index = (int) (Math.random() * messages.length);

            View rootView = requireActivity().findViewById(android.R.id.content);

            Snackbar.make(rootView, messages[index], Snackbar.LENGTH_LONG)
                    .setAction("Okay", v -> openFragment(new ServicesFragment()))
                    .show();
        }
    }
    private void openFragment(Fragment fragment) {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.nav_host_fragment_content_main, fragment); // 👈 Use your container ID
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(JobsViewModel.class);
    }
    private void setupRecyclerView() {
        // Initialize with empty list - this is crucial
        adapter = new JobsAdapter(new ArrayList<>(), job -> {
            if (isDetailsShowing) return;

            isDetailsShowing = true;
            int viewCount = prefs.getInt("job_views", 0) + 1;
            prefs.edit().putInt("job_views", viewCount).apply();

            Runnable showDetails = () -> {
                if (getParentFragmentManager().findFragmentByTag("JobDetails") == null) {
                    JobDetailsBottomSheet detailsSheet = JobDetailsBottomSheet.newInstance(job.getId());
                    detailsSheet.show(getParentFragmentManager(), "JobDetails");
                }
                isDetailsShowing = false;
            };

            if (viewCount % 5 == 0) {
                showRewardedInterstitialAd(showDetails);
            } else {
                showDetails.run();
            }
        });

        binding.rvJobs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvJobs.setAdapter(adapter);
    }

    private List<Object> buildDisplayListWithAds(List<Job> jobs) {
        List<Object> displayList = new ArrayList<>();

        if (jobs.isEmpty()) {
            return displayList;
        }

        int adIndex = 0;

        for (int i = 0; i < jobs.size(); i++) {
            // Always add the job first
            displayList.add(jobs.get(i));

            // Insert ad after every 4th job (positions 4, 8, 12, etc.)
            if ((i + 1) % 4 == 0 && adIndex < nativeAdPool.size()) {
                NativeAd ad = nativeAdPool.get(adIndex);
                if (ad != null) {
                    displayList.add(ad);
                    adIndex++;
                }
            }
        }

        // Load more ads if needed
        int totalAdsNeeded = (jobs.size() / 4) + 1;
        if (nativeAdPool.size() < totalAdsNeeded) {
            loadMoreNativeAds(totalAdsNeeded - nativeAdPool.size());
        }

        Log.d("AdsDebug", "Built list with " + jobs.size() + " jobs and " + adIndex + " ads");
        return displayList;
    }

private void initializeNativeAds() {
    adLoader = new AdLoader.Builder(requireContext(), NATIVE_AD_UNIT_ID)
            .forNativeAd(new NativeAd.OnNativeAdLoadedListener() {
                @Override
                public void onNativeAdLoaded(NativeAd nativeAd) {
                    nativeAdPool.add(nativeAd);
                    Log.d("AdsDebug", "Native ad loaded. Pool size: " + nativeAdPool.size());

                    // Refresh the list when new ads are loaded
                    if (viewModel.getJobs().getValue() != null && !viewModel.getJobs().getValue().isEmpty()) {
                        List<Job> currentJobs = viewModel.getJobs().getValue();
                        List<Object> displayList = buildDisplayListWithAds(currentJobs);
                        adapter.updateDisplayList(displayList);
                    }
                }
            })
            .withAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(LoadAdError adError) {
                    Log.e("AdsDebug", "Native ad failed to load: " + adError.getMessage());
                }
            })
            .build();

    // Load initial ads
    loadMoreNativeAds(3); // Start with 3 ads
}
    private void loadMoreNativeAds(int count) {
        if (adLoader != null) {
            adLoader.loadAds(new AdRequest.Builder().build(), count);
        }
    }

    private void showRewardedInterstitialAd(Runnable onAdDismissed) {
        if (rewardedInterstitialAd == null) {
            AdRequest adRequest = new AdRequest.Builder().build();
            RewardedInterstitialAd.load(requireContext(), AD_UNIT_ID, adRequest,
                    new RewardedInterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(RewardedInterstitialAd ad) {
                            rewardedInterstitialAd = ad;
                            showLoadedAd(onAdDismissed);
                        }

                        @Override
                        public void onAdFailedToLoad(LoadAdError loadAdError) {
                            rewardedInterstitialAd = null;
                            onAdDismissed.run();
                        }
                    });
        } else {
            showLoadedAd(onAdDismissed);
        }
    }

    private void showLoadedAd(Runnable onAdDismissed) {
        rewardedInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                rewardedInterstitialAd = null;
                onAdDismissed.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                rewardedInterstitialAd = null;
                onAdDismissed.run();
            }
        });

        rewardedInterstitialAd.show(requireActivity(), rewardItem -> {
            // Reward earned
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


    // In the JobsListFragment class, add these methods and update the observers:

    private void initViews() {
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String query = binding.etSearch.getText().toString().trim();
            viewModel.searchJobs(query);
            return true;
        });

        binding.btnSearch.setOnClickListener(v -> {
            String query = binding.etSearch.getText().toString().trim();
            viewModel.searchJobs(query);
        });

        binding.btnLoadMore.setOnClickListener(v -> viewModel.loadMoreJobs());

        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadJobs(true);
        });

        // Setup refresh button for empty state
        binding.btnRefresh.setOnClickListener(v -> {
            refreshJobs();
        });
    }

    private void setupObservers() {
        viewModel.getJobs().observe(getViewLifecycleOwner(), jobs -> {
            if (jobs != null && !jobs.isEmpty()) {
                // Convert List<Job> to List<Object> with ads inserted
                List<Object> displayList = buildDisplayListWithAds(jobs);
                adapter.updateDisplayList(displayList);
                binding.rvJobs.setVisibility(View.VISIBLE);
                binding.layoutNoJobs.setVisibility(View.GONE);
                binding.btnRefresh.setVisibility(View.GONE);
            } else {
                binding.rvJobs.setVisibility(View.GONE);
                binding.layoutNoJobs.setVisibility(View.VISIBLE);
                binding.btnRefresh.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.pbLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnLoadMore.setEnabled(!isLoading);
            if (isLoading) {
                binding.btnLoadMore.setText("Loading...");
                binding.btnRefresh.setVisibility(View.GONE);
                binding.layoutNoJobs.setVisibility(View.GONE);
            } else {
                binding.btnLoadMore.setText("Load More Jobs");
            }
            if (!isLoading) {
                binding.swipeRefresh.setRefreshing(false);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                // Show refresh button on error
                binding.layoutNoJobs.setVisibility(View.VISIBLE);
                binding.btnRefresh.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getHasMoreData().observe(getViewLifecycleOwner(), hasMore -> {
            binding.btnLoadMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
        });
    }

    private void refreshJobs() {
        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.layoutNoJobs.setVisibility(View.GONE);
        binding.btnRefresh.setVisibility(View.GONE);
        viewModel.loadJobs(true);
    }
}