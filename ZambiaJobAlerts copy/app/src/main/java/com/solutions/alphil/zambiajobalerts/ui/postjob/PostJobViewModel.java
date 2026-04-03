package com.solutions.alphil.zambiajobalerts.ui.postjob;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PostJobViewModel extends AndroidViewModel {

    private final MutableLiveData<Boolean> _isPosting = new MutableLiveData<>(false);
    public LiveData<Boolean> isPosting() {
        return _isPosting;
    }

    private final MutableLiveData<String> _postResult = new MutableLiveData<>();
    public LiveData<String> getPostResult() {
        return _postResult;
    }

    private static final String PREF_NAME = "wp_creds";
    private static final String KEY_WP_PWD = "wp_password";
    private static final String DEFAULT_PWD = "k1rE Jvud syGP cbmI y1HN hItI";
    private static final String WP_USER = "lavum27@gmail.com";

    // Site URLs and Passwords from Node.js code
    private static final String URL_ZAMBIA = "https://zambiajobalerts.com/wp-json/wp/v2/job-listings/";
    private static final String URL_MANCHINTO = "https://manchinto.com/wp-json/wp/v2/job-listings/";
    private static final String URL_ZINSTABLOG = "https://zinstablog.com/wp-json/wp/v2/job-listings/";

    private static final String PWD_MANCHINTO = "b3Xn 07gY vZpg 4LnS PRqI bDBe";
    private static final String PWD_ZINSTABLOG = "RvNV WeQu b6WD KuJK 8Hbd rB9K";

    // Webpushr Credentials
    private static final String WEBPUSHR_KEY = "82d976ee01019162668f6f92cec308fb";
    private static final String WEBPUSHR_AUTH = "88373";

    private final SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();

    public PostJobViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void postJob(String title, String company, String location, String description, String applicationLink, List<Integer> categoryIds, Integer jobTypeId) {
        if (title.isEmpty() || company.isEmpty() || location.isEmpty() || description.isEmpty() || applicationLink.isEmpty()) {
            _postResult.setValue("Please fill in all fields");
            return;
        }

        if (title.startsWith("UPDATE_PWD:")) {
            String newPwd = title.replace("UPDATE_PWD:", "").trim();
            prefs.edit().putString(KEY_WP_PWD, newPwd).apply();
            _postResult.setValue("API Password updated successfully");
            return;
        }

        _isPosting.setValue(true);

        String dynamicPassword = prefs.getString(KEY_WP_PWD, DEFAULT_PWD);

        try {
            JSONObject json = new JSONObject();
            json.put("status", "publish");
            json.put("title", title);
            json.put("content", description);
            
            JSONObject meta = new JSONObject();
            meta.put("_job_location", location);
            meta.put("_company_name", company);
            meta.put("_application", applicationLink);
            json.put("meta", meta);
            
            JSONArray catArray = new JSONArray();
            if (categoryIds != null && !categoryIds.isEmpty()) {
                for (Integer id : categoryIds) catArray.put(id);
            } else {
                catArray.put(25);
            }
            json.put("job-categories", catArray);

            JSONArray typeArray = new JSONArray();
            if (jobTypeId != null) {
                typeArray.put(jobTypeId);
            } else {
                typeArray.put(6);
            }
            json.put("job-types", typeArray);

            String jsonString = json.toString();

            // Post to all three sites
            postToSite(URL_ZAMBIA, WP_USER, dynamicPassword, jsonString, title, company);
            //postToSite(URL_MANCHINTO, WP_USER, PWD_MANCHINTO, jsonString, null, null);
            //postToSite(URL_ZINSTABLOG, WP_USER, PWD_ZINSTABLOG, jsonString, null, null);

        } catch (Exception e) {
            _isPosting.setValue(false);
            _postResult.setValue("Error creating request: " + e.getMessage());
        }
    }

    private void postToSite(String url, String user, String password, String jsonBody, String titleForPush, String companyForPush) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        String auth = user + ":" + password;
        String encodedAuth = Base64.encodeToString(auth.getBytes(), Base64.NO_WRAP);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Basic " + encodedAuth)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("PostJob", "Failed to post to " + url + ": " + e.getMessage());
                if (url.equals(URL_ZAMBIA)) {
                    _isPosting.postValue(false);
                    _postResult.postValue("Failed to post to main site: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Log.d("PostJob", "Successfully posted to " + url);
                    if (url.equals(URL_ZAMBIA)) {
                        _isPosting.postValue(false);
                        _postResult.postValue("Job posted successfully!");
                        
                        // Send Push Notification after successful main post
                        try {
                            JSONObject respJson = new JSONObject(responseData);
                            String jobUrl = respJson.optString("link", "https://zambiajobalerts.com");
                            sendWebpushrNotification(titleForPush, jobUrl, companyForPush);
                        } catch (Exception e) {
                            Log.e("PostJob", "Error parsing response for push: " + e.getMessage());
                        }
                    }
                } else {
                    Log.e("PostJob", "Error from " + url + ": " + response.code() + " " + responseData);
                    if (url.equals(URL_ZAMBIA)) {
                        _isPosting.postValue(false);
                        _postResult.postValue("Main site error: " + response.code());
                    }
                }
            }
        });
    }

    private void sendWebpushrNotification(String title, String url, String company) {
        if (title == null || url == null || company == null) return;
        try {
            String message = title + " Wanted for employment at " + company + " for details click apply button";
            
            JSONObject payload = new JSONObject();
            payload.put("title", title);
            payload.put("message", message);
            payload.put("target_url", url);
            
            JSONArray buttons = new JSONArray();
            JSONObject applyBtn = new JSONObject();
            applyBtn.put("title", "Apply");
            applyBtn.put("url", url);
            buttons.put(applyBtn);
            payload.put("action_buttons", buttons);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url("https://api.webpushr.com/v1/notification/send/all")
                    .post(body)
                    .addHeader("webpushrKey", WEBPUSHR_KEY)
                    .addHeader("webpushrAuthToken", WEBPUSHR_AUTH)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("Webpushr", "Failed to send notification: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    Log.d("Webpushr", "Notification sent: " + response.code());
                }
            });

        } catch (Exception e) {
            Log.e("Webpushr", "Error preparing notification: " + e.getMessage());
        }
    }
}