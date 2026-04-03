package com.solutions.alphil.zambiajobalerts.ui.aigenerate;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.solutions.alphil.zambiajobalerts.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CVGeneratorFragment extends Fragment {

    private static final String TAG = "CVGeneratorFragment";
    private static final String PREFS_NAME = "CVDataPrefs";
    private static final String KEY_GENERATED_LINKS = "generated_links";
    private static final String KEY_GENERATED_TEXTS = "generated_texts";
    private static final String API_URL = "https://zambiajobalerts.com/system/api/generate-texts";

    // Media type constants
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // Selection options
    private final String[] documentTypes = {"CV/Resume", "Cover Letter"};
    private final String[] documentFormats = {"PDF Document", "Word Document", "Text Only"};
    private final String[] letterTones = {"Formal", "Professional", "Confident", "Friendly", "Enthusiastic"};

    // UI Components
    private TextView selectedTypeText, selectedFormatText, selectedToneText;
    private Button selectTypeBtn, selectFormatBtn, selectToneBtn;
    private LinearLayout toneSelectionLayout, cvFields, coverLetterFields, resultsLayout;
    private EditText nameInput, emailInput, phoneInput, positionInput, educationInput,
            experienceInput, skillsInput, companyInput, positionApplyingInput,
            experienceSummaryInput, addNotesInput;
    private Button submitBtn, viewGeneratedBtn, copyTextBtn, downloadBtn;
    private TextView resultTextView;

    private AlertDialog loadingDialog;
    private RewardedAd rewardedAd;
    private static final String TEST_AD_UNIT_ID_REWARDED = "ca-app-pub-2168080105757285/1720477714";

    private String selectedType = "cv";
    private String selectedFormat = "pdf";
    private String selectedTone = "formal";
    private String deviceId;

    // Store current generation results
    private String currentFileLink = "";
    private String currentTextResult = "";

    private final OkHttpClient client = new OkHttpClient();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_cv_generator, container, false);

        // Initialize device ID
        deviceId = getDeviceId();

        // Initialize UI components
        initializeUI(root);

        // Initialize ads
        initializeAds(root);

        return root;
    }
    private String getDeviceId() {
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String uniqueID = prefs.getString("device_uuid", null);
        if (uniqueID == null) {
            uniqueID = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("device_uuid", uniqueID).apply();
        }
        return uniqueID;
    }
    /*
    private String getDeviceId() {
        if (getContext() == null) return "unknown_device";

        SharedPreferences prefs = getContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", null);

        if (deviceId == null) {
            deviceId = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            if (deviceId == null) {
                deviceId = "device_" + System.currentTimeMillis();
            }
            prefs.edit().putString("device_id", deviceId).apply();
        }

        return deviceId;
    }*/

    private void initializeUI(View root) {
        // Initialize selection views
        selectedTypeText = root.findViewById(R.id.selectedTypeText);
        selectedFormatText = root.findViewById(R.id.selectedFormatText);
        selectedToneText = root.findViewById(R.id.selectedToneText);

        selectTypeBtn = root.findViewById(R.id.selectTypeBtn);
        selectFormatBtn = root.findViewById(R.id.selectFormatBtn);
        selectToneBtn = root.findViewById(R.id.selectToneBtn);

        toneSelectionLayout = root.findViewById(R.id.toneSelectionLayout);
        cvFields = root.findViewById(R.id.cvFields);
        coverLetterFields = root.findViewById(R.id.coverLetterFields);
        resultsLayout = root.findViewById(R.id.resultsLayout);

        // Initialize input fields
        nameInput = root.findViewById(R.id.nameInput);
        emailInput = root.findViewById(R.id.emailInput);
        phoneInput = root.findViewById(R.id.phoneInput);
        positionInput = root.findViewById(R.id.positionInput);
        educationInput = root.findViewById(R.id.educationInput);
        experienceInput = root.findViewById(R.id.experienceInput);
        skillsInput = root.findViewById(R.id.skillsInput);
        companyInput = root.findViewById(R.id.companyInput);
        positionApplyingInput = root.findViewById(R.id.positionApplyingInput);
        experienceSummaryInput = root.findViewById(R.id.experienceSummaryInput);
        addNotesInput = root.findViewById(R.id.addNotesInput);

        // Initialize action buttons
        submitBtn = root.findViewById(R.id.submitButton);
        viewGeneratedBtn = root.findViewById(R.id.viewGeneratedBtn);
        copyTextBtn = root.findViewById(R.id.copyTextBtn);
        downloadBtn = root.findViewById(R.id.downloadBtn);

        // Initialize result text view
        resultTextView = root.findViewById(R.id.resultTextView);

        // Make result text clickable for links
        resultTextView.setMovementMethod(LinkMovementMethod.getInstance());
        resultTextView.setAutoLinkMask(Linkify.WEB_URLS);
        resultTextView.setTextIsSelectable(true);

        // Set click listeners for selection buttons
        selectTypeBtn.setOnClickListener(v -> showDocumentTypeDialog());
        selectFormatBtn.setOnClickListener(v -> showFormatDialog());
        selectToneBtn.setOnClickListener(v -> showToneDialog());

        // Also make the text views clickable
        selectedTypeText.setOnClickListener(v -> showDocumentTypeDialog());
        selectedFormatText.setOnClickListener(v -> showFormatDialog());
        selectedToneText.setOnClickListener(v -> showToneDialog());

        // Set click listeners for action buttons
        submitBtn.setOnClickListener(v -> validateAndSubmit());
        viewGeneratedBtn.setOnClickListener(v -> showGeneratedDocumentsDialog());
        copyTextBtn.setOnClickListener(v -> copyCurrentTextToClipboard());
        downloadBtn.setOnClickListener(v -> downloadCurrentFile());

        // Set initial values
        selectedTypeText.setText("CV/Resume");
        selectedFormatText.setText("PDF Document");
        selectedToneText.setText("Formal");
    }

    private void initializeAds(View root) {
        try {
            AdView adViewTop = root.findViewById(R.id.adViewTop);
            AdView adViewBottom = root.findViewById(R.id.adViewBottom);
            AdRequest adRequest = new AdRequest.Builder().build();
            adViewTop.loadAd(adRequest);
            adViewBottom.loadAd(adRequest);
        } catch (Exception e) {
            Log.e(TAG, "Ad initialization failed: " + e.getMessage());
        }
    }

    private void showDocumentTypeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Document Type");

        int currentIndex = selectedType.equals("cv") ? 0 : 1;

        builder.setSingleChoiceItems(documentTypes, currentIndex, (dialog, which) -> {
            selectedType = which == 0 ? "cv" : "cover_letter";
            selectedTypeText.setText(documentTypes[which]);
            updateFieldsVisibility();
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showFormatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Document Format");

        int currentIndex = selectedFormat.equals("pdf") ? 0 : 1;

        builder.setSingleChoiceItems(documentFormats, currentIndex, (dialog, which) -> {
           // selectedFormat
            switch (which) {
                case 0:
                    selectedFormat = "pdf";
                    break;
                case 1:
                    selectedFormat = "docx";
                    break;
                case 2:
                    selectedFormat = "txt";
                    break;
                default:
                    selectedFormat = "docx";

            }
            //which == 0 ? "pdf" : "text";
            selectedFormatText.setText(documentFormats[which]);
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showToneDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Writing Tone");

        int currentIndex = 0;
        switch (selectedTone) {
            case "formal": currentIndex = 0; break;
            case "professional": currentIndex = 1; break;
            case "confident": currentIndex = 2; break;
            case "friendly": currentIndex = 3; break;
            case "enthusiastic": currentIndex = 4; break;
        }

        builder.setSingleChoiceItems(letterTones, currentIndex, (dialog, which) -> {
            selectedTone = letterTones[which].toLowerCase().replace(" ", "_");
            selectedToneText.setText(letterTones[which]);
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void updateFieldsVisibility() {
        if (selectedType.equals("cv")) {
            cvFields.setVisibility(View.VISIBLE);
            coverLetterFields.setVisibility(View.GONE);
            toneSelectionLayout.setVisibility(View.GONE);
        } else {
            cvFields.setVisibility(View.GONE);
            coverLetterFields.setVisibility(View.VISIBLE);
            toneSelectionLayout.setVisibility(View.VISIBLE);
        }
    }

    private void validateAndSubmit() {
        // Basic validation
        if (nameInput.getText().toString().trim().isEmpty() ||
                emailInput.getText().toString().trim().isEmpty() ||
                phoneInput.getText().toString().trim().isEmpty()) {

            Toast.makeText(getContext(), "Please fill all required fields!", Toast.LENGTH_SHORT).show();
            return;
        }

        // CV-specific validation
        if (selectedType.equals("cv") && positionInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(getContext(), "Please enter position/job title!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Cover letter-specific validation
        if (selectedType.equals("cover_letter") &&
                (companyInput.getText().toString().trim().isEmpty() ||
                        positionApplyingInput.getText().toString().trim().isEmpty())) {
            Toast.makeText(getContext(), "Please enter company and position!", Toast.LENGTH_SHORT).show();
            return;
        }
        submitBtn.setEnabled(false);
        showLoadingDialog("Loading ad, please wait...");
        loadRewardedAdRewarded();
    }

    private void generateDocument() {
        showLoadingDialog("Generating your " + selectedType + "...");

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("type", selectedType);
            requestBody.put("format", selectedFormat);
            requestBody.put("name", nameInput.getText().toString().trim());
            requestBody.put("device_id", deviceId);

            // Common fields
            if (!addNotesInput.getText().toString().trim().isEmpty()) {
                requestBody.put("additional_notes", addNotesInput.getText().toString().trim());
            }

            // CV specific fields
            if (selectedType.equals("cv")) {
                requestBody.put("position", positionInput.getText().toString().trim());
                if (!educationInput.getText().toString().trim().isEmpty()) {
                    requestBody.put("education", educationInput.getText().toString().trim());
                }
                if (!experienceInput.getText().toString().trim().isEmpty()) {
                    requestBody.put("experience", experienceInput.getText().toString().trim());
                }
                if (!skillsInput.getText().toString().trim().isEmpty()) {
                    requestBody.put("skills", skillsInput.getText().toString().trim());
                }
            }
            // Cover letter specific fields
            else {
                requestBody.put("company", companyInput.getText().toString().trim());
                requestBody.put("position", positionApplyingInput.getText().toString().trim());
                requestBody.put("tone", selectedTone);
                if (!experienceSummaryInput.getText().toString().trim().isEmpty()) {
                    requestBody.put("experience", experienceSummaryInput.getText().toString().trim());
                }
            }

            Log.d(TAG, "Sending request: " + requestBody.toString());

            // Use the non-deprecated method for creating RequestBody
            RequestBody body = RequestBody.create(requestBody.toString(), JSON);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        dismissLoadingDialog();
                        showResult("❌ REQUEST FAILED\n\nError: " + e.getMessage());
                        Toast.makeText(getContext(), "Generation failed!", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String responseBody = response.body().string();
                    Log.d(TAG, "Response: " + responseBody);

                    requireActivity().runOnUiThread(() -> {
                        dismissLoadingDialog();

                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                handleSuccessResponse(jsonResponse);
                            } catch (JSONException e) {
                                showResult("❌ PARSE ERROR\n\nError parsing response: " + e.getMessage());
                            }
                        } else {
                            showResult("❌ SERVER ERROR\n\nError: " + response.code() + " - " + responseBody);
                        }
                    });
                }
            });

        } catch (JSONException e) {
            dismissLoadingDialog();
            showResult("❌ REQUEST ERROR\n\nError creating request: " + e.getMessage());
        }
    }

    private void handleSuccessResponse(JSONObject response) throws JSONException {
        StringBuilder result = new StringBuilder();
        result.append("✅ GENERATION SUCCESSFUL\n\n");

        // Display response details
        String documentType = selectedType.equals("cv") ? "CV/Resume" : "Cover Letter";
        result.append("Document: ").append(documentType).append("\n");

        if (response.has("status")) {
            result.append("Status: ").append(response.getString("status")).append("\n");
        }
        if (response.has("format")) {
            String format = response.getString("format");
            result.append("Format: ").append(format.toUpperCase()).append("\n");
        }

        result.append("\n");

        // Reset current results
        currentFileLink = "";
        currentTextResult = "";

        if (response.has("file_link")) {
            currentFileLink = response.getString("file_link");
            saveGeneratedLink(currentFileLink);
            result.append("📄 DOWNLOAD LINK:\n").append(currentFileLink).append("\n\n");
            result.append("✅ Link has been saved to your documents.\n");
            downloadBtn.setVisibility(View.VISIBLE);
        } else {
            downloadBtn.setVisibility(View.GONE);
        }

        if (response.has("text_result")) {
            currentTextResult = response.getString("text_result");
            saveGeneratedText(currentTextResult);
            result.append("\n📝 GENERATED TEXT:\n").append(currentTextResult).append("\n\n");
            result.append("✅ Text has been saved and can be copied.");
        }

        showResult(result.toString());
        Toast.makeText(getContext(), documentType + " generated successfully!", Toast.LENGTH_LONG).show();
    }

    private void showResult(String result) {
        requireActivity().runOnUiThread(() -> {
            resultTextView.setText(result);
            resultsLayout.setVisibility(View.VISIBLE);

            // Simple focus request
            resultsLayout.requestFocus();

            // Or scroll to bottom of the scroll view
            ScrollView scrollView = requireView().findViewById(R.id.mainScrollView);
            if (scrollView != null) {
                scrollView.post(() -> {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                });
            }
        });
       /* resultTextView.setText(result);
        resultsLayout.setVisibility(View.VISIBLE);

        // Scroll to results
        resultsLayout.post(() -> {
            View parent = (View) resultsLayout.getParent();
            parent.scrollTo(0, resultsLayout.getTop());
            //parent.scrollTo(0, resultsLayout.getTop());
        });
        */
    }

    private void copyCurrentTextToClipboard() {
        if (!currentTextResult.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Generated Document", currentTextResult);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Text copied to clipboard!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "No text available to copy", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadCurrentFile() {
        if (!currentFileLink.isEmpty()) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentFileLink));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Cannot open download link", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "No download link available", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveGeneratedLink(String link) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> links = new HashSet<>(prefs.getStringSet(KEY_GENERATED_LINKS, new HashSet<>()));
        links.add(link);
        prefs.edit().putStringSet(KEY_GENERATED_LINKS, links).apply();
    }

    private void saveGeneratedText(String text) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> texts = new HashSet<>(prefs.getStringSet(KEY_GENERATED_TEXTS, new HashSet<>()));
        texts.add(text);
        prefs.edit().putStringSet(KEY_GENERATED_TEXTS, texts).apply();
    }

    private void showGeneratedDocumentsDialog() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> links = prefs.getStringSet(KEY_GENERATED_LINKS, new HashSet<>());
        Set<String> texts = prefs.getStringSet(KEY_GENERATED_TEXTS, new HashSet<>());

        if (links.isEmpty() && texts.isEmpty()) {
            Toast.makeText(getContext(), "No generated documents found", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("My Generated Documents");

        StringBuilder message = new StringBuilder();

        if (!links.isEmpty()) {
            message.append("📄 Download Links (Click to open):\n\n");
            int linkCount = 1;
            for (String link : links) {
                message.append(linkCount).append(". ").append(link).append("\n\n");
                linkCount++;
            }
        }

        if (!texts.isEmpty()) {
            message.append("📝 Generated Texts:\n\n");
            int textCount = 1;
            for (String text : texts) {
                String displayText = text.length() > 100 ? text.substring(0, 100) + "..." : text;
                message.append(textCount).append(". ").append(displayText).append("\n\n");
                textCount++;
            }
        }

        builder.setMessage(message.toString());
        builder.setPositiveButton("OK", null);

        if (!texts.isEmpty()) {
            builder.setNeutralButton("Copy Latest Text", (dialog, which) -> {
                String latestText = texts.iterator().next();
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Generated Text", latestText);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Text copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        }

        // Add option to clear all documents
        builder.setNegativeButton("Clear All", (dialog, which) -> {
            clearAllDocuments();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Make links clickable in the dialog
        TextView textView = dialog.findViewById(android.R.id.message);
        if (textView != null) {
            textView.setAutoLinkMask(Linkify.WEB_URLS);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void clearAllDocuments() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_GENERATED_LINKS)
                .remove(KEY_GENERATED_TEXTS)
                .apply();
        Toast.makeText(getContext(), "All documents cleared", Toast.LENGTH_SHORT).show();
    }

    private void showLoadingDialog(String message) {
        if (getContext() == null) return;

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
                        Toast.makeText(getContext(), "Ad failed to load. Generating your document...", Toast.LENGTH_SHORT).show();
                        generateDocument();
                    }
                });
    }

    private void showRewardedAdRewarded() {
        if (rewardedAd != null && getActivity() != null) {
            rewardedAd.show(getActivity(), rewardItem -> {
                // Reward the user and generate document
                int rewardAmount = rewardItem.getAmount();
                String rewardType = rewardItem.getType();
                Log.d(TAG, "Rewarded ad completed. Reward: " + rewardAmount + " " + rewardType);

                // After ad is completed, generate document
                generateDocument();
            });
        } else {
            Toast.makeText(getContext(), "Ad not available. Generating your document...", Toast.LENGTH_SHORT).show();
            generateDocument();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up resources
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}