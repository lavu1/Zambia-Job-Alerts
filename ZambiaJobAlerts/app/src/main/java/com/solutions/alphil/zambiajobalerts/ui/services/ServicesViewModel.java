
package com.solutions.alphil.zambiajobalerts.ui.services;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class ServicesViewModel extends AndroidViewModel {

    private static final String PREFS_NAME = "ad_prefs";
    private static final String KEY_ADS_WATCHED = "ads_watched";
    private static final String KEY_JOBS_VIEWED = "jobs_viewed";

    private final MutableLiveData<Integer> adsWatched;
    private final MutableLiveData<Integer> jobsViewed;
    private final SharedPreferences prefs;

    public ServicesViewModel(@NonNull Application application) {
        super(application);

        prefs = application.getSharedPreferences(PREFS_NAME, 0);

        // Load saved values from SharedPreferences
        int savedAds = prefs.getInt(KEY_ADS_WATCHED, 0);
        int savedJobs = prefs.getInt(KEY_JOBS_VIEWED, 0);

        adsWatched = new MutableLiveData<>(savedAds);
        jobsViewed = new MutableLiveData<>(savedJobs);
    }

    public LiveData<Integer> getAdsWatched() {
        return adsWatched;
    }

    public LiveData<Integer> getJobsViewed() {
        return jobsViewed;
    }

    public void addAdWatched() {
        int newCount = adsWatched.getValue() + 1;
        adsWatched.setValue(newCount);
        prefs.edit().putInt(KEY_ADS_WATCHED, newCount).apply();
    }

    public void addJobViewed() {
        int newCount = jobsViewed.getValue() + 1;
        jobsViewed.setValue(newCount);
        prefs.edit().putInt(KEY_JOBS_VIEWED, newCount).apply();
    }

    public void resetAds() {
        adsWatched.setValue(0);
        prefs.edit().putInt(KEY_ADS_WATCHED, 0).apply();
    }

    // NEW: Method to deduct specific number of ads
    public void deductAds(int amount) {
        int currentAds = adsWatched.getValue();
        if (currentAds >= amount) {
            int newCount = currentAds - amount;
            adsWatched.setValue(newCount);
            prefs.edit().putInt(KEY_ADS_WATCHED, newCount).apply();
        }
    }

    // NEW: Method to get current ad count
    public int getCurrentAdCount() {
        return adsWatched.getValue();
    }
}
