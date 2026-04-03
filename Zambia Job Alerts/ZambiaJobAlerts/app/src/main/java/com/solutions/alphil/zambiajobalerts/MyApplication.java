package com.solutions.alphil.zambiajobalerts;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.ads.MobileAds;
import com.google.firebase.messaging.FirebaseMessaging;
import com.solutions.alphil.zambiajobalerts.classes.AdManager;
import com.solutions.alphil.zambiajobalerts.classes.RefreshDataWorker;

import java.util.concurrent.TimeUnit;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        MobileAds.initialize(this, initializationStatus -> {
            // Pre-cache ads after initialization
            AdManager.getInstance().loadInterstitialAd(this);
        });

        // Schedule periodic background sync
        scheduleJobSync();

        // Subscribe to topic
        FirebaseMessaging.getInstance().subscribeToTopic("new_jobs")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Log success if needed
                    }
                });
    }

    private void scheduleJobSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                RefreshDataWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "JobSyncWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );
    }
}
