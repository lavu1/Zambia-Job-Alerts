package com.solutions.alphil.zambiajobalerts.classes;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import java.util.concurrent.TimeUnit;

public class AdManager {
    private static final String TAG = "AdManager";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"; // Test ID
    private static final long REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);

    private InterstitialAd mInterstitialAd;
    private static AdManager instance;
    private boolean isLoading;
    private long lastInterstitialLoadTime;

    private AdManager() {}

    public static synchronized AdManager getInstance() {
        if (instance == null) {
            instance = new AdManager();
        }
        return instance;
    }

    public synchronized void loadInterstitialAd(Context context) {
        if (context == null || isLoading || !shouldRefreshInterstitial()) {
            return;
        }

        isLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                        lastInterstitialLoadTime = System.currentTimeMillis();
                        isLoading = false;
                        Log.i(TAG, "onAdLoaded");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.i(TAG, loadAdError.getMessage());
                        mInterstitialAd = null;
                        isLoading = false;
                    }
                });
    }

    public synchronized boolean isInterstitialAdLoaded() {
        if (isInterstitialAdStale()) {
            clearInterstitialAd();
        }
        return mInterstitialAd != null;
    }

    public synchronized InterstitialAd getInterstitialAd() {
        if (isInterstitialAdStale()) {
            clearInterstitialAd();
        }
        return mInterstitialAd;
    }
    
    public synchronized void clearInterstitialAd() {
        mInterstitialAd = null;
        lastInterstitialLoadTime = 0L;
    }

    public synchronized boolean shouldRefreshInterstitial() {
        return mInterstitialAd == null || isInterstitialAdStale();
    }

    private boolean isInterstitialAdStale() {
        return lastInterstitialLoadTime > 0
                && System.currentTimeMillis() - lastInterstitialLoadTime >= REFRESH_INTERVAL_MS;
    }
}
