package com.solutions.alphil.zambiajobalerts.ui.rewards;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.solutions.alphil.zambiajobalerts.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CVReviewFragment extends Fragment {

    private static final String TAG = "CVReviewFragment";
    private EditText emailInput, phoneInput, fileInput, changesInput;
    private Button submitBtn;
    private Uri selectedFileUri;
    private String selectedFileName;
    private File tempUploadFile;
    private AlertDialog loadingDialog;
    private RewardedAd rewardedAd;
    private static final String TEST_AD_UNIT_ID_REWARDED = "ca-app-pub-2168080105757285/1720477714";

    // Variables to store form data for later use
    private String pendingEmail, pendingPhone, pendingChanges;

    private final OkHttpClient client = new OkHttpClient();

    // Modern Activity Result API for file picking
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_cv_review, container, false);

        AdView adView = root.findViewById(R.id.adViewCvReview);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        emailInput = root.findViewById(R.id.emailInput);
        phoneInput = root.findViewById(R.id.phoneInput);
        fileInput = root.findViewById(R.id.fileInput);
        changesInput = root.findViewById(R.id.changesInput);
        submitBtn = root.findViewById(R.id.submitButton);

        // Set up file input click listener to open file picker
        fileInput.setOnClickListener(v -> openFilePicker());

        submitBtn.setOnClickListener(v -> {
            if (emailInput.getText().toString().isEmpty() ||
                    phoneInput.getText().toString().isEmpty() ||
                    selectedFileUri == null) {
                Toast.makeText(getContext(), "Please fill all fields and select a file!", Toast.LENGTH_SHORT).show();
            } else {
                // Store the form data
                pendingEmail = emailInput.getText().toString();
                pendingPhone = phoneInput.getText().toString();
                pendingChanges = changesInput.getText().toString();

                showLoadingDialog("Loading ad, please wait...");

                // Load and show rewarded ad first, then upload file to server
                loadRewardedAdRewarded();
            }
        });

        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up temporary file when fragment is destroyed
        if (tempUploadFile != null && tempUploadFile.exists()) {
            tempUploadFile.delete();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Set MIME types for PDF and Word documents
        String[] mimeTypes = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        // Add permission to persistable URI permission
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        try {
            // Clean up previous temp file if exists
            if (tempUploadFile != null && tempUploadFile.exists()) {
                tempUploadFile.delete();
            }

            // Take persistable permission
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            selectedFileUri = uri;
            selectedFileName = getFileName(uri);

            if (selectedFileName != null) {
                fileInput.setText(selectedFileName);
                Toast.makeText(getContext(), "File selected: " + selectedFileName, Toast.LENGTH_SHORT).show();
            } else {
                fileInput.setText("File selected");
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error accessing file", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;

        try {
            if ("content".equals(uri.getScheme())) {
                DocumentFile documentFile = DocumentFile.fromSingleUri(requireContext(), uri);
                if (documentFile != null && documentFile.getName() != null) {
                    fileName = documentFile.getName();
                } else {
                    // Fallback: query the content resolver
                    try (var cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex("_display_name");
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex);
                            }
                        }
                    }
                }
            }

            // Final fallback
            if (fileName == null) {
                String path = uri.getPath();
                if (path != null) {
                    int cut = path.lastIndexOf('/');
                    if (cut != -1) {
                        fileName = path.substring(cut + 1);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return fileName;
    }

    private void uploadSelectedFile() {
        if (selectedFileUri == null) {
            Toast.makeText(getContext(), "No file selected!", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingDialog("Uploading CV for Review...");

        new Thread(() -> {
            try {
                // Create a temporary file from the URI
                tempUploadFile = createTempFileFromUri(selectedFileUri);
                if (tempUploadFile != null && tempUploadFile.exists()) {
                    // Verify file exists and can be read
                    if (tempUploadFile.length() > 0) {
                        uploadFileWithOkHttp(tempUploadFile);
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            dismissLoadingDialog();
                            Toast.makeText(getContext(), "File is empty or inaccessible", Toast.LENGTH_LONG).show();
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        dismissLoadingDialog();
                        Toast.makeText(getContext(), "Error creating temporary file", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(getContext(), "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private File createTempFileFromUri(Uri uri) throws IOException {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new IOException("Cannot open input stream from URI");
            }

            // Create temporary file with proper extension
            String extension = getFileExtension(selectedFileName);
            String prefix = "cv_upload";
            String suffix = extension != null ? "." + extension : ".tmp";

            File tempFile = File.createTempFile(prefix, suffix, requireContext().getCacheDir());

            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();

            // Verify the file was created successfully
            if (tempFile.exists() && tempFile.length() > 0) {
                return tempFile;
            } else {
                throw new IOException("Failed to create temporary file");
            }

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private void uploadFileWithOkHttp(File file) {
        try {
            // Determine MIME type
            String mimeType = "application/pdf";
            if (selectedFileName != null) {
                if (selectedFileName.endsWith(".doc")) {
                    mimeType = "application/msword";
                } else if (selectedFileName.endsWith(".docx")) {
                    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
            }

            // Create multipart request body
            RequestBody fileBody = RequestBody.create(MediaType.parse(mimeType), file);

            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("type", "cv_review")
                    .addFormDataPart("email", pendingEmail)
                    .addFormDataPart("phone", pendingPhone)
                    .addFormDataPart("additional_notes", pendingChanges)
                    .addFormDataPart("cv_file_path", selectedFileName, fileBody)
                    .build();

            Request request = new Request.Builder()
                    .url("https://zambiajobalerts.com/system/api/services")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Upload failed: " + e.getMessage());
                    requireActivity().runOnUiThread(() -> {
                        dismissLoadingDialog();
                        Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });

                    // Clean up temp file
                    if (file.exists()) {
                        file.delete();
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String responseBody = response.body().string();
                    Log.d(TAG, "Server response: " + responseBody);

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

                    // Clean up temp file
                    if (file.exists()) {
                        file.delete();
                    }
                    tempUploadFile = null;
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Upload error: " + e.getMessage());
            requireActivity().runOnUiThread(() -> {
                dismissLoadingDialog();
                Toast.makeText(getContext(), "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });

            // Clean up temp file
            if (file != null && file.exists()) {
                file.delete();
            }
            tempUploadFile = null;
        }
    }

    private void resetForm() {
        requireActivity().runOnUiThread(() -> {
            emailInput.setText("");
            phoneInput.setText("");
            fileInput.setText("");
            changesInput.setText("");
            selectedFileUri = null;
            selectedFileName = null;

            // Clean up temp file
            if (tempUploadFile != null && tempUploadFile.exists()) {
                tempUploadFile.delete();
                tempUploadFile = null;
            }
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
                        // If ad fails to load, upload file directly
                        Toast.makeText(getContext(), "Ad failed to load. Uploading your file...", Toast.LENGTH_SHORT).show();
                        uploadSelectedFile();
                    }
                });
    }

    private void showRewardedAdRewarded() {
        if (rewardedAd != null) {
            rewardedAd.show(requireActivity(), rewardItem -> {
                // Reward the user
                int rewardAmount = rewardItem.getAmount();
                String rewardType = rewardItem.getType();

                // After ad is completed, upload file to server
                uploadSelectedFile();
            });
        } else {
            // If no ad is available, upload file directly
            Toast.makeText(getContext(), "Ad not available. Uploading your file...", Toast.LENGTH_SHORT).show();
            uploadSelectedFile();
        }
    }
}