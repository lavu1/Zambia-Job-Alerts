package com.solutions.alphil.zambiajobalerts.ui.services;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.solutions.alphil.zambiajobalerts.R;
import com.solutions.alphil.zambiajobalerts.ui.rewards.*;
import com.solutions.alphil.zambiajobalerts.ui.services.ServicesViewModel;

import java.util.ArrayList;
import java.util.List;

public class ServicesFragment extends Fragment {

    private ServicesViewModel viewModel;
    private TextView adsWatchedView, jobsViewedView, rewardsView;
    private Button watchAdButton, redeemButton;

    private RewardedInterstitialAd rewardedInterstitialAd;
    private RewardedAd rewardedAd;
    private static final String TEST_AD_UNIT_ID_REWARDED = "ca-app-pub-2168080105757285/9306188221";


    private static final String TEST_AD_UNIT_ID = "ca-app-pub-2168080105757285/9306188221"; // Test Ad Unit
    private int lastRewardTier = 0; // track last unlocked tier
    private boolean isLoadingAd = false;
    private AlertDialog loadingDialog;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_services, container, false);

//        AdView adView = root.findViewById(R.id.adViewServicesmain); // Assuming you add android:id="@+id/adView" to the XML
//        AdRequest adRequest = new AdRequest.Builder().build();
//        adView.loadAd(adRequest);

//        AdView adViewB = root.findViewById(R.id.adViewServicesmainBottom); // Assuming you add android:id="@+id/adView" to the XML
//        AdRequest adRequestB = new AdRequest.Builder().build();
//        adViewB.loadAd(adRequestB);

        adsWatchedView = root.findViewById(R.id.countedValue);
        jobsViewedView = root.findViewById(R.id.jobsViewed);
        rewardsView = root.findViewById(R.id.rewardsText);
        watchAdButton = root.findViewById(R.id.watchAdButton);
        redeemButton = root.findViewById(R.id.redeemButton);

        viewModel = new ViewModelProvider(this).get(ServicesViewModel.class);

        // Initialize Ads
      //  MobileAds.initialize(requireContext());

        // Observers
        viewModel.getAdsWatched().observe(getViewLifecycleOwner(), count -> {
            adsWatchedView.setText("Ads Watched: " + count);
            updateRewards(count);
            checkForNewUnlock(count, root);
        });

        viewModel.getJobsViewed().observe(getViewLifecycleOwner(), count ->
                jobsViewedView.setText("Jobs Viewed: " + count)
        );

        // In onCreateView, replace the current watchAdButton click listener:
        watchAdButton.setOnClickListener(v -> {
            watchAdButton.setEnabled(false);
            watchAdButton.setText("Loading...");
            loadRewardedAd();
        });

        // Watch Ad → show rewarded ad
       // watchAdButton.setOnClickListener(v -> loadRewardedAd());

        // Redeem rewards
        redeemButton.setOnClickListener(v -> handleRedeem());

        return root;
    }

    // Load Rewarded Ad
    private void loadRewardedAd() {
        if (isLoadingAd) {
            return; // Prevent multiple clicks
        }

        isLoadingAd = true;

        // Show loading dialog
        showLoadingDialog("Loading video ad...");

        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedInterstitialAd.load(requireContext(), TEST_AD_UNIT_ID, adRequest,
                new RewardedInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedInterstitialAd ad) {
                        dismissLoadingDialog();
                        isLoadingAd = false;
                        rewardedInterstitialAd = ad;
                        showRewardedAd();

                        watchAdButton.setEnabled(true);
                        watchAdButton.setText("Watch Ad to Earn Reward");
                    }

                    @Override
                    public void onAdFailedToLoad(com.google.android.gms.ads.LoadAdError loadAdError) {
                        dismissLoadingDialog();
                        isLoadingAd = false;
                        watchAdButton.setEnabled(true);
                        watchAdButton.setText("Watch Ad to Earn Reward");
                        Toast.makeText(getContext(), "Failed to load ad. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void showLoadingDialog(String message) {
        if (getContext() == null) return;
        loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Please Wait")
                .setMessage(message)
                .setCancelable(true)
                .create();
        loadingDialog.show();

    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null) {
            loadingDialog.dismiss();
        }
    }
    private void showRewardedAd() {
        if (rewardedInterstitialAd == null) {
            Toast.makeText(getContext(), "Ad not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }

        rewardedInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                rewardedInterstitialAd = null;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                rewardedInterstitialAd = null;
            }
        });

        rewardedInterstitialAd.show(requireActivity(), rewardItem -> {
            // ✅ Ad watched completely → increment ads counter
            viewModel.addAdWatched();
            Toast.makeText(getContext(), "🎉 Reward earned!", Toast.LENGTH_SHORT).show();
        });
    }

    // Redeem services based on ads watched
    private void handleRedeem() {
        int ads = viewModel.getAdsWatched().getValue();

        if (ads >= 10) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Choose Service")
                    .setItems(new String[]{
                            "Email Alerts (1 ad)",
                            "Phone Alerts (1 ad)",
                            "Priority Job Applications (3 ads)",
                            "CV Review/Write (5 ads)",
                            "Career Coaching (7 ads)"
                    }, (dialog, which) -> {
                        switch (which) {
                            case 0: // Email Alerts - 1 ad
                                if (ads >= 1) {
                                    openFragment(new EmailJobAlertsFragment());
                                    viewModel.deductAds(1);
                                }
                                break;
                            case 1: // Phone Alerts - 1 ad
                                if (ads >= 1) {
                                    openFragment(new PhoneJobAlertsFragment());
                                    viewModel.deductAds(1);
                                }
                                break;
                            case 2: // Priority Job Applications - 3 ads
                                if (ads >= 3) {
                                    openFragment(new PriorityJobFragment());
                                    viewModel.deductAds(3);
                                }
                                break;
                            case 3: // CV Services - 5 ads
                                if (ads >= 5) {
                                    new MaterialAlertDialogBuilder(requireContext())
                                            .setTitle("Choose CV Service")
                                            .setItems(new String[]{"CV Review / Update (5 ads)", "CV Write from Scratch (5 ads)"}, (dialog2, which2) -> {
                                                if (which2 == 0) openFragment(new CVReviewFragment());
                                                else openFragment(new CVWriteFragment());
                                                viewModel.deductAds(5);
                                            }).show();
                                }
                                break;
                            case 4: // Career Coaching - 7 ads
                                if (ads >= 7) {
                                    openFragment(new CareerCoachingFragment());
                                    viewModel.deductAds(7);
                                }
                                break;
                        }
                    }).show();
        } else {
            // Show individual options based on available ads
            showIndividualOptions(ads);
        }
    }

    // NEW: Method to show individual options when user doesn't have 10 ads
    private void showIndividualOptions(int ads) {
        List<String> options = new ArrayList<>();
        final List<Integer> costs = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        if (ads >= 1) {
            options.add("Email Alerts (1 ad)");
            costs.add(1);
            actions.add(() -> openFragment(new EmailJobAlertsFragment()));

            options.add("Phone Alerts (1 ad)");
            costs.add(1);
            actions.add(() -> openFragment(new PhoneJobAlertsFragment()));
        }

        if (ads >= 3) {
            options.add("Priority Job Applications (3 ads)");
            costs.add(3);
            actions.add(() -> openFragment(new PriorityJobFragment()));
        }

        if (ads >= 5) {
            options.add("CV Review / Update (5 ads)");
            costs.add(5);
            actions.add(() -> openFragment(new CVReviewFragment()));

            options.add("CV Write from Scratch (5 ads)");
            costs.add(5);
            actions.add(() -> openFragment(new CVWriteFragment()));
        }

        if (ads >= 7) {
            options.add("Career Coaching Session (7 ads)");
            costs.add(7);
            actions.add(() -> openFragment(new CareerCoachingFragment()));
        }

        if (options.isEmpty()) {
            Toast.makeText(getContext(), "Watch more ads to unlock rewards!", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Redeem Rewards (" + ads + " ads available)")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    int cost = costs.get(which);
                    if (ads >= cost) {
                        viewModel.deductAds(cost);
                        actions.get(which).run();
                    } else {
                        Toast.makeText(getContext(), "Not enough ads!", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    // Updated reward names
    private String rewardName(int tier) {
        switch (tier) {
            case 1: return "Job Alerts";
            case 3: return "Priority Job Applications";
            case 5: return "CV Services";
            case 7: return "Career Coaching";
            default: return "Reward";
        }
    }

    // Updated rewards display
    private void updateRewards(int adsWatched) {
        StringBuilder sb = new StringBuilder("🔥 Available Rewards:\n");
        sb.append("Ads Available: ").append(adsWatched).append("\n\n");

        sb.append(adsWatched >= 1 ? "✅ Job Alerts (1 ad each)\n" : "❌ Job Alerts (1 ad)\n");
        sb.append(adsWatched >= 3 ? "✅ Priority Job Applications (3 ads)\n" : "❌ Priority Job Applications (3 ads)\n");
        sb.append(adsWatched >= 5 ? "✅ CV Services (5 ads)\n" : "❌ CV Services (5 ads)\n");
        sb.append(adsWatched >= 7 ? "✅ Career Coaching Session (7 ads)\n" : "❌ Career Coaching Session (7 ads)\n");

        rewardsView.setText(sb.toString());
    }
    // Show new unlock notification
    private void checkForNewUnlock(int adsWatched, View root) {
        int newTier = 0;

        if (adsWatched >= 10) newTier = 10;
        else if (adsWatched >= 5) newTier = 5;
        else if (adsWatched >= 3) newTier = 3;
        else if (adsWatched >= 1) newTier = 1;

        if (newTier > lastRewardTier) {
            Snackbar.make(root, "🎉 New Reward Unlocked! (" + rewardName(newTier) + ")", Snackbar.LENGTH_LONG)
                    .setAction("Redeem", v -> handleRedeem())
                    .show();
            lastRewardTier = newTier;
        }
    }


    private void openFragment(Fragment fragment) {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.nav_host_fragment_content_main, fragment); // 👈 Use your container ID
        transaction.addToBackStack(null);
        transaction.commit();
    }

    //rewarded ad
    private void loadRewardedAdRewarded() {
        if (isLoadingAd) {
            return; // Prevent multiple clicks
        }

        isLoadingAd = true;

        // Show loading dialog
        showLoadingDialog("Loading video ad...");

        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(requireContext(), TEST_AD_UNIT_ID_REWARDED, adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        dismissLoadingDialog();
                        isLoadingAd = false;
                        rewardedAd = ad;
                        showRewardedAdRewarded(); // Call your show method after ad is loaded

                        watchAdButton.setEnabled(true);
                        watchAdButton.setText("Watch Ad to Earn Reward");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        dismissLoadingDialog();
                        isLoadingAd = false;
                        watchAdButton.setEnabled(true);
                        watchAdButton.setText("Watch Ad to Earn Reward");
                        Toast.makeText(getContext(), "Failed to load ad. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void showRewardedAdRewarded() {
        if (rewardedAd != null) {
            rewardedAd.show(requireActivity(), rewardItem -> {
                // Reward the user
                int rewardAmount = rewardItem.getAmount();
                String rewardType = rewardItem.getType();
                Toast.makeText(getContext(),
                        "You earned " + rewardAmount + " " + rewardType,
                        Toast.LENGTH_SHORT).show();
                // Grant reward logic here
            });
        } else {
            Toast.makeText(getContext(), "Ad not ready. Please load again.", Toast.LENGTH_SHORT).show();
        }
    }


}
