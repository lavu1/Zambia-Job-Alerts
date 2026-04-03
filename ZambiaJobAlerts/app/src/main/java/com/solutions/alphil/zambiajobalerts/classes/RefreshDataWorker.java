package com.solutions.alphil.zambiajobalerts.classes;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RefreshDataWorker extends Worker {
    public RefreshDataWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("RefreshDataWorker", "Starting background sync...");
        
        // 1. Refresh Job Data
        JobRepository repository = new JobRepository((android.app.Application) getApplicationContext());
        repository.refreshJobs();
        
        // 2. Refresh/Pre-cache Ads
        try {
            if (AdManager.getInstance().shouldRefreshInterstitial()) {
                AdManager.getInstance().loadInterstitialAd(getApplicationContext());
            }
        } catch (Exception e) {
            Log.e("RefreshDataWorker", "Error pre-caching ads: " + e.getMessage());
        }

        return Result.success();
    }
}
