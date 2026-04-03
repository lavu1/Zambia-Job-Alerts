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

public class CVWriteFragment extends Fragment {

    private static final String SERVER_URL = "https://zambiajobalerts.com/system/api/services";
    private EditText nameInput, emailInput, addNotes, phoneInput, educationInput, workInput, skillsInput;
    private Button submitBtn;
    private Context context;
    private OkHttpClient client;
    private AlertDialog loadingDialog;
    private RewardedAd rewardedAd;
    private static final String TEST_AD_UNIT_ID_REWARDED = "ca-app-pub-2168080105757285/1720477714";

    // Variables to store form data for later use
    private String pendingName, pendingEmail, pendingPhone, pendingEducation, pendingWork, pendingSkills, pendingAddNotes;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_cv_write, container, false);

        AdView adView = root.findViewById(R.id.adViewCvWrite);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        client = new OkHttpClient();
        context = getContext();

        nameInput = root.findViewById(R.id.nameInput);
        emailInput = root.findViewById(R.id.emailInput);
        phoneInput = root.findViewById(R.id.phoneInput);
        educationInput = root.findViewById(R.id.educationInput);
        workInput = root.findViewById(R.id.workInput);
        skillsInput = root.findViewById(R.id.skillsInput);
        addNotes = root.findViewById(R.id.addNotesInput);
        submitBtn = root.findViewById(R.id.submitButton);

        submitBtn.setOnClickListener(v -> {
            if (nameInput.getText().toString().isEmpty() ||
                    emailInput.getText().toString().isEmpty() ||
                    phoneInput.getText().toString().isEmpty()) {

                Toast.makeText(getContext(), "Fill required fields!", Toast.LENGTH_SHORT).show();
            } else {
                // Store the form data
                pendingName = nameInput.getText().toString();
                pendingEmail = emailInput.getText().toString();
                pendingPhone = phoneInput.getText().toString();
                pendingEducation = educationInput.getText().toString();
                pendingWork = workInput.getText().toString();
                pendingSkills = skillsInput.getText().toString();
                pendingAddNotes = addNotes.getText().toString();

                showLoadingDialog("Loading ad, please wait...");
                Toast.makeText(getContext(), "CV Write Request Submitted!", Toast.LENGTH_LONG).show();

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

    public void SendInforToServer(String nameInput, String emailInput, String phoneInput, String educationInput,
                                  String workInput, String skillsInput, String addNotes) {

        if (!isNetworkAvailable()) {
            Toast.makeText(context, "You are not connected to the network", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingDialog("Submitting information please wait...");

        RequestBody formBody = new FormBody.Builder()
                .add("type", "Write CV")
                .add("days", "")
                .add("name", nameInput)
                .add("email", emailInput)
                .add("phone", phoneInput)
                .add("education_background", educationInput)
                .add("work_experience", workInput)
                .add("skills", skillsInput)
                .add("additional_notes", addNotes)
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
                            Toast.makeText(getContext(), "CV submitted successfully!", Toast.LENGTH_LONG).show();
                            resetForm();
                        } else {
                            Toast.makeText(getContext(), "Upload response: " + responseBody, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Upload failed: " + response.code() + " - " + responseBody, Toast.LENGTH_LONG).show();
                    }
                });
                response.close();
            }
        });
    }

    private void resetForm() {
        requireActivity().runOnUiThread(() -> {
            nameInput.setText("");
            emailInput.setText("");
            phoneInput.setText("");
            educationInput.setText("");
            workInput.setText("");
            skillsInput.setText("");
            addNotes.setText("");
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
                        SendInforToServer(pendingName, pendingEmail, pendingPhone, pendingEducation, pendingWork, pendingSkills, pendingAddNotes);
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
                SendInforToServer(pendingName, pendingEmail, pendingPhone, pendingEducation, pendingWork, pendingSkills, pendingAddNotes);
            });
        } else {
            // If no ad is available, send data to server directly
            Toast.makeText(getContext(), "Ad not available. Submitting your request...", Toast.LENGTH_SHORT).show();
            SendInforToServer(pendingName, pendingEmail, pendingPhone, pendingEducation, pendingWork, pendingSkills, pendingAddNotes);
        }
    }
}