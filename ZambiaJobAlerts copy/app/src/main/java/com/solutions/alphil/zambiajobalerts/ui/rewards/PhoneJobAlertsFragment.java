package com.solutions.alphil.zambiajobalerts.ui.rewards;

import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.solutions.alphil.zambiajobalerts.R;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PhoneJobAlertsFragment extends Fragment {

    private EditText phoneInput, jobCategoryInput;
    private Button submitBtn;
    private Context context;
    private OkHttpClient client;
    private AlertDialog loadingDialog;
    private static final String SERVER_URL = "https://zambiajobalerts.com/system/api/services";
    private RewardedAd rewardedAd;
    private static final String TEST_AD_UNIT_ID_REWARDED = "ca-app-pub-2168080105757285/1720477714";

    // Variables to store form data for later use
    private String pendingPhone;
    private String pendingJobCategory;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_phone_job_alerts, container, false);

        AdView adView = root.findViewById(R.id.adViewPhoneJob);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        phoneInput = root.findViewById(R.id.phoneInput);
        jobCategoryInput = root.findViewById(R.id.jobCategoryInput);
        submitBtn = root.findViewById(R.id.submitButton);
        context = getContext();
        client = new OkHttpClient();

        submitBtn.setOnClickListener(v -> {
            if (phoneInput.getText().toString().isEmpty()) {
                Toast.makeText(getContext(), "Enter your phone!", Toast.LENGTH_SHORT).show();
            } else {
                // Store the form data
                pendingPhone = phoneInput.getText().toString();
                pendingJobCategory = jobCategoryInput.getText().toString();

                showLoadingDialog("Loading ad, please wait...");
                Toast.makeText(getContext(), "Phone Job Alerts Activated! for the next two days", Toast.LENGTH_LONG).show();

                // Load and show rewarded ad first, then send data to server
                loadRewardedAdRewarded();
            }
        });

        return root;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    public void SendInforToServer(String jobCategoryInputs, String phoneInput) {
        if (!isNetworkAvailable()) {
            Toast.makeText(context, "You are not connected to the network", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingDialog("Submitting information please wait...");

        RequestBody formBody = new FormBody.Builder()
                .add("type", "Share me Jobs")
                .add("days", "2")
                .add("name", "")
                .add("email", "")
                .add("phone", phoneInput)
                .add("education_background", "")
                .add("work_experience", "")
                .add("skills", "")
                .add("additional_notes", jobCategoryInputs)
                .add("status", "Pending")
                .build();

        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(formBody)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(getContext(), "Failed to submit request. Please try again.", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body().string();

                requireActivity().runOnUiThread(() -> {
                    dismissLoadingDialog();
                    if (response.isSuccessful()) {
                        if (responseBody.contains("id") || responseBody.contains("updated_at")) {
                            Toast.makeText(getContext(), "Request submitted successfully!", Toast.LENGTH_LONG).show();
                            resetForm();
                        } else {
                            Toast.makeText(getContext(), "Response: " + responseBody, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Failed: " + response.code() + " - " + responseBody, Toast.LENGTH_LONG).show();
                    }
                });
                response.close();
            }
        });
    }

    private void resetForm() {
        requireActivity().runOnUiThread(() -> {
            jobCategoryInput.setText("");
            phoneInput.setText("");
        });
    }

    private void showLoadingDialog(String message) {
        if (getContext() == null) return;

        // Dismiss existing dialog if shown
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }

        loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Please Wait")
                .setMessage(message)
                .setCancelable(false)
                .create();
        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void loadRewardedAdRewarded() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(requireContext(), TEST_AD_UNIT_ID_REWARDED, adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        dismissLoadingDialog();
                        rewardedAd = ad;
                        showRewardedAdRewarded();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        dismissLoadingDialog();
                        // If ad fails to load, send data to server directly
                        Toast.makeText(getContext(), "Ad failed to load. Submitting your request...", Toast.LENGTH_SHORT).show();
                        SendInforToServer(pendingJobCategory, pendingPhone);
                    }
                });
    }

    private void showRewardedAdRewarded() {
        if (rewardedAd != null) {
            rewardedAd.show(requireActivity(), rewardItem -> {
                // Reward the user
                int rewardAmount = rewardItem.getAmount();
                String rewardType = rewardItem.getType();

                // After ad is completed, send data to server
                SendInforToServer(pendingJobCategory, pendingPhone);
            });
        } else {
            // If no ad is available, send data to server directly
            Toast.makeText(getContext(), "Ad not available. Submitting your request...", Toast.LENGTH_SHORT).show();
            SendInforToServer(pendingJobCategory, pendingPhone);
        }
    }
}