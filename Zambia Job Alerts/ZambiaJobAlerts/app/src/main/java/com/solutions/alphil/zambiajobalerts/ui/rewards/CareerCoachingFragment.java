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

public class CareerCoachingFragment extends Fragment {

    private EditText nameInput, emailInput, careerProfileInput;
    private Button submitBtn;
    private Context context;
    private OkHttpClient client;
    private AlertDialog loadingDialog;
    private static final String SERVER_URL = "https://zambiajobalerts.com/system/api/services";
    private RewardedAd rewardedAd;
    private static final String TEST_AD_UNIT_ID_REWARDED = "ca-app-pub-2168080105757285/1720477714";

    // Variables to store form data for later use
    private String pendingName, pendingEmail, pendingCareerProfile;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_career_coaching, container, false);

        AdView adViewTop = root.findViewById(R.id.adViewCareerCoachingTop);
        AdRequest adRequestTop = new AdRequest.Builder().build();
        adViewTop.loadAd(adRequestTop);

        AdView adView = root.findViewById(R.id.adViewCareerCoaching);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        context = getContext();
        client = new OkHttpClient();

        nameInput = root.findViewById(R.id.nameInput);
        emailInput = root.findViewById(R.id.emailInput);
        careerProfileInput = root.findViewById(R.id.careerProfileInput);
        submitBtn = root.findViewById(R.id.submitButton);

        submitBtn.setOnClickListener(v -> {
            if (nameInput.getText().toString().isEmpty() || emailInput.getText().toString().isEmpty()) {
                Toast.makeText(getContext(), "Please fill in details!", Toast.LENGTH_SHORT).show();
            } else {
                // Store the form data
                pendingName = nameInput.getText().toString();
                pendingEmail = emailInput.getText().toString();
                pendingCareerProfile = careerProfileInput.getText().toString();

                showLoadingDialog("Loading ad, please wait...");
                Toast.makeText(getContext(), "Career Coaching Request Submitted!", Toast.LENGTH_LONG).show();

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

    public void SendInforToServer(String nameInput, String emailInput, String careerInput) {
        if (!isNetworkAvailable()) {
            Toast.makeText(context, "You are not connected to the network", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingDialog("Submitting information please wait...");

        RequestBody formBody = new FormBody.Builder()
                .add("type", "Career Coaching")
                .add("days", "0")
                .add("name", nameInput)
                .add("email", emailInput)
                .add("phone", "")
                .add("education_background", "")
                .add("work_experience", "")
                .add("skills", "")
                .add("additional_notes", careerInput)
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
            careerProfileInput.setText("");
            emailInput.setText("");
            nameInput.setText("");
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
                        SendInforToServer(pendingName, pendingEmail, pendingCareerProfile);
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
                SendInforToServer(pendingName, pendingEmail, pendingCareerProfile);
            });
        } else {
            // If no ad is available, send data to server directly
            Toast.makeText(getContext(), "Ad not available. Submitting your request...", Toast.LENGTH_SHORT).show();
            SendInforToServer(pendingName, pendingEmail, pendingCareerProfile);
        }
    }
}