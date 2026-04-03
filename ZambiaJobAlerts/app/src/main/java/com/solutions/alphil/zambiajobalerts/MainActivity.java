package com.solutions.alphil.zambiajobalerts;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.play.core.review.ReviewException;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.review.model.ReviewErrorCode;
import com.solutions.alphil.zambiajobalerts.classes.JobDetailsBottomSheet;
import com.solutions.alphil.zambiajobalerts.databinding.ActivityMainBinding;
import com.solutions.alphil.zambiajobalerts.ui.aigenerate.CVGeneratorFragment;
import com.solutions.alphil.zambiajobalerts.ui.jobs.JobsListFragment;
import com.solutions.alphil.zambiajobalerts.ui.home.HomeFragment;
import com.solutions.alphil.zambiajobalerts.ui.gallery.GalleryFragment;
import com.solutions.alphil.zambiajobalerts.ui.postjob.PostJobFragment;
import com.solutions.alphil.zambiajobalerts.ui.savedjobs.SavedJobsFragment;
import com.solutions.alphil.zambiajobalerts.ui.savedjobs.SavedJobsReminderWorker;
import com.solutions.alphil.zambiajobalerts.ui.services.ServicesFragment;
import com.solutions.alphil.zambiajobalerts.ui.slideshow.SlideshowFragment;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String CHANNEL_ID = "job_alerts_channel";
    private static final String PREFS_NAME = "app_prefs";
    private static final String NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked";
    private NavController navController;
    private SharedPreferences prefs;
    private InterstitialAd interstitialAd;
    private static final String AD_UNIT_ID = "ca-app-pub-2168080105757285/4046795138"; // Test ID; replace with your real interstitial ad unit ID
    private static final String PREFS_NAMEC = "app_prefs";
    private static final String KEY_APP_OPENS = "app_opens";
    private RewardedAd rewardedAd;
    private static final String TEST_AD_UNIT_ID_REWARDED = "ca-app-pub-3940256099942544/5224354917";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "You are not connected to the internet", Toast.LENGTH_SHORT).show();
            System.exit(0);
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREFS_NAMEC, Context.MODE_PRIVATE);

        ReviewManager manager = ReviewManagerFactory.create(this);
        Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                ReviewInfo reviewInfo = task.getResult();
            } else {
                @ReviewErrorCode int reviewErrorCode = ((ReviewException) task.getException()).getErrorCode();
            }
        });

        if (savedInstanceState == null) {
            int openCount = prefs.getInt(KEY_APP_OPENS, 0) + 1;
            prefs.edit().putInt(KEY_APP_OPENS, openCount).apply();
            Log.d("AdLogic", "App Open Count: " + openCount);

            if (openCount % 5 == 0) {
                loadAndShowInterstitialAd();
            }
        }
        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (navController != null) {
                    navController.navigate(R.id.nav_post_job);
                } else {
                    loadFragment(new PostJobFragment(), "PostJob");
                }
            }
        });


        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        initializeNavigation(drawer, navigationView);

        createNotificationChannel();
        setupHamburgerMenu();
        checkNotificationPermission();

        handleNotificationIntent(getIntent());
        handleDeepLink(getIntent());
        
        // Schedule daily reminder for saved jobs
        scheduleSavedJobsReminder();

        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                    setEnabled(false);           // temporarily disable our callback
                    getOnBackPressedDispatcher().onBackPressed();  // trigger default behavior
                    setEnabled(true);            // re-enable for next time
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void scheduleSavedJobsReminder() {
        PeriodicWorkRequest reminderRequest = new PeriodicWorkRequest.Builder(
                SavedJobsReminderWorker.class,
                24, TimeUnit.HOURS, // Once every day
                1, TimeUnit.HOURS // Flexibility window
        ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "SavedJobsReminder",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderRequest
        );
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private void handleDeepLink(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri data = intent.getData();
            String path = data.getPath(); // e.g., /job/ict-technician-x2/

            if (path != null && path.startsWith("/job/")) {
                // Extract everything after /job/ and remove trailing slashes
                String identifier = path.substring("/job/".length()).replaceAll("/", "");

                if (!identifier.isEmpty()) {
                    navigateToJobDetails(identifier);
                }
            }
        }
    }

    private void navigateToJobDetails(String identifier) {
        try {
            // Try to see if it's a numeric ID first
            int jobId = Integer.parseInt(identifier);
            JobDetailsBottomSheet.newInstance(jobId)
                    .show(getSupportFragmentManager(), "JobDetails");
        } catch (NumberFormatException e) {
            // It's a slug (e.g., "ict-technician-x2")
            JobDetailsBottomSheet.newInstance(identifier)
                    .show(getSupportFragmentManager(), "JobDetails");
        }
    }

    private void navigateToJobDetails(int jobId) {
        if (navController == null) {
            Log.w("MainActivity", "NavController not ready yet");
            return;
        }

        Bundle args = new Bundle();
        args.putInt("open_job_id", jobId);   // or "job_id" — be consistent

        try {
            navController.navigate(R.id.nav_jobs, args);
        } catch (Exception e) {
            Log.e("Nav", "Navigation failed", e);
            // fallback: show bottom sheet directly (see below)
            JobDetailsBottomSheet.newInstance(jobId)
                    .show(getSupportFragmentManager(), "JobDetails");
        }
    }
    private void loadAndShowInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, AD_UNIT_ID, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        if (!isFinishing() && !isDestroyed()) {
                            showInterstitialAd();
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        interstitialAd = null;
                        // Proceed without ad; log error if needed
                    }
                });
    }

    private void showInterstitialAd() {
        if (interstitialAd != null) {
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    // App continues normally
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    interstitialAd = null;
                    // Proceed without ad
                }
            });
            interstitialAd.show(this);
            resetAppOpenCounter();
        }
    }

    // Optional: Reset counter if needed (e.g., via settings)
    public void resetAppOpenCounter() {
        prefs.edit().putInt(KEY_APP_OPENS, 0).apply();
    }

    private void setupHamburgerMenu() {
        try {
            // Set up the hamburger menu icon
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);
            }

            // Add toggle for drawer
            androidx.appcompat.app.ActionBarDrawerToggle toggle = new androidx.appcompat.app.ActionBarDrawerToggle(
                    this,
                    binding.drawerLayout,
                    binding.appBarMain.toolbar,
                    R.string.navigation_drawer_open,
                    R.string.navigation_drawer_close
            );

            binding.drawerLayout.addDrawerListener(toggle);
            toggle.syncState();

        } catch (Exception e) {
            Log.e("MainActivity", "Error setting up hamburger menu", e);

            // Fallback: Add manual menu button
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            binding.appBarMain.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (binding.drawerLayout.isDrawerOpen(binding.navView)) {
                        binding.drawerLayout.closeDrawer(binding.navView);
                    } else {
                        binding.drawerLayout.openDrawer(binding.navView);
                    }
                }
            });
        }
    }
    /**
     * Safe navigation initialization that works with or without Navigation Component
     */
    private void initializeNavigation(DrawerLayout drawer, NavigationView navigationView) {
        try {
            // Try to use Navigation Component
            navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);

            // Setup Navigation Component
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_jobs,R.id.nav_rewards, R.id.nav_post_job, R.id.nav_saved_jobs)
                    .setOpenableLayout(drawer)
                    .build();

            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);

            Log.d("MainActivity", "Navigation Component initialized successfully");

        } catch (Exception e) {
            Log.e("MainActivity", "Navigation Component failed, using manual navigation", e);

            // Fallback to manual navigation
            setupManualNavigation(navigationView);

            // Load default fragment
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                loadFragment(new JobsListFragment(), "JobsList");
            }
        }
    }

    /**
     * Manual navigation setup as fallback
     */
    private void setupManualNavigation(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                Fragment fragment = null;
                String tag = null;

                if (id == R.id.nav_home) {
                    fragment = new HomeFragment();
                    tag = "Home";
                } else if (id == R.id.nav_jobs) {
                    fragment = new JobsListFragment();
                    tag = "JobsList";
                } else if (id == R.id.nav_gallery) {
                    fragment = new GalleryFragment();
                    tag = "Gallery";
                } else if (id == R.id.nav_slideshow) {
                    fragment = new SlideshowFragment();
                    tag = "Slideshow";
                } else if (id == R.id.nav_rewards) {
                    fragment = new ServicesFragment();
                    tag = "rewards";
                }else if (id == R.id.nav_ai) {
                    fragment = new CVGeneratorFragment();
                    tag = "aiservices";
                } else if (id == R.id.nav_post_job) {
                    fragment = new PostJobFragment();
                    tag = "PostJob";
                } else if (id == R.id.nav_saved_jobs) {
                    fragment = new SavedJobsFragment();
                    tag = "SavedJobs";
                }

                if (fragment != null) {
                    loadFragment(fragment, tag);
                }

                // Close drawer
                binding.drawerLayout.closeDrawers();
                return true;
            }
        });
    }

    /**
     * Load fragment manually
     */
    private void loadFragment(Fragment fragment, String tag) {
        try {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Try to replace in nav_host_fragment_content_main first
            try {
                transaction.replace(R.id.nav_host_fragment_content_main, fragment, tag);
            } catch (Exception e) {
                // Fallback to content frame
                Log.w("MainActivity", "nav_host_fragment_content_main not found, using content frame");
                transaction.replace(android.R.id.content, fragment, tag);
            }

            transaction.addToBackStack(tag);
            transaction.commit();

        } catch (Exception e) {
            Log.e("MainActivity", "Error loading fragment: " + e.getMessage());
            Toast.makeText(this, "Error loading content refresh again", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== NOTIFICATION CONTROL METHODS ====================

    /**
     * Enable notifications in the app
     */
    public void enableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Request permission first
                requestNotificationPermission();
                return;
            }
        }
       // Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
        // Enable notifications in our app settings
        MyFirebaseMessagingService.setNotificationsEnabled(this, true);
        ///Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
        updateNotificationMenuIcon();
        Log.d("MainActivity", "Notifications enabled by user");
    }

    /**
     * Disable notifications in the app
     */
    public void disableNotifications() {
        MyFirebaseMessagingService.setNotificationsEnabled(this, false);
        //Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
        updateNotificationMenuIcon();
        Log.d("MainActivity", "Notifications disabled by user");
    }
    private void updateNotificationMenuIcon() {
        // Update the options menu
        invalidateOptionsMenu();

        // You can also update the toolbar if you want a visible indicator
        //updateToolbarNotificationIndicator();
    }

    /**
     * Check if notifications are enabled in app settings
     */
    public boolean areNotificationsEnabledInApp() {
        return MyFirebaseMessagingService.isNotificationsEnabled(this);
    }

    /**
     * Check if system notification permission is granted
     */
    public boolean areSystemNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // For older versions, assume enabled
    }

    /**
     * Toggle notifications on/off
     */
    public void toggleNotifications() {
        if (areNotificationsEnabledInApp()) {
            disableNotifications();
        } else {
            enableNotifications();
        }
    }

    /**
     * Show notification settings dialog
     */
    public void showNotificationSettingsDialog() {
        boolean currentlyEnabled = areNotificationsEnabledInApp();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Notification Settings")
                .setMessage(currentlyEnabled ?
                        "Notifications are currently enabled. You'll receive alerts for new jobs." :
                        "Notifications are currently disabled. You won't receive job alerts.")
                .setPositiveButton(currentlyEnabled ? "Disable" : "Enable", (dialog, which) -> {
                    toggleNotifications();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("System Settings", (dialog, which) -> {
                    openSystemNotificationSettings();
                })
                .show();
    }

    // ==================== NOTIFICATION PERMISSION METHODS ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Job Alerts";
            String description = "Notifications for new job postings and updates";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void checkNotificationPermission() {
        // Check if we've already asked for permission
        boolean alreadyAsked = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(NOTIFICATION_PERMISSION_ASKED, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                if (!alreadyAsked) {
                    // First time - show explanation dialog
                    showNotificationPermissionDialog();
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    // User denied before - show rationale
                    showPermissionRationaleDialog();
                }
            } else {
                // Permission already granted
                setupNotificationServices();
            }
        } else {
            // For older Android versions, permission is granted by default
            setupNotificationServices();
        }
    }

    private void showNotificationPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Enable Notifications")
                .setMessage("Stay updated with the latest job opportunities! Allow notifications to receive alerts when new jobs are posted that match your preferences.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    // Mark that we've asked for permission
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(NOTIFICATION_PERMISSION_ASKED, true)
                            .apply();

                    requestNotificationPermission();
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    // Mark that we've asked for permission
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(NOTIFICATION_PERMISSION_ASKED, true)
                            .apply();

                    // Disable notifications in app settings
                    disableNotifications();
                    Toast.makeText(this, "You can enable notifications later in Settings", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void showPermissionRationaleDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Notifications Disabled")
                .setMessage("You're missing out on job opportunities! Notifications help you stay updated with new job postings. Please enable them to get the most out of Zambia Job Alerts.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    requestNotificationPermission();
                })
                .setNegativeButton("Keep Disabled", (dialog, which) -> {
                    disableNotifications();
                })
                .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - enable notifications
                enableNotifications();
                //Toast.makeText(this, "Notifications enabled! You'll receive job alerts.", Toast.LENGTH_SHORT).show();
                setupNotificationServices();
            } else {
                // Permission denied - disable notifications
                disableNotifications();
                //Toast.makeText(this, "Notifications disabled. You can enable them in Settings.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupNotificationServices() {
        // Enable notifications when services are set up
        enableNotifications();
        //Toast.makeText(this, "Notification services activated", Toast.LENGTH_SHORT).show();
    }

    private void openSystemNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        }
        startActivity(intent);
    }

    // ==================== NOTIFICATION INTENT HANDLING ====================

    private void handleNotificationIntent(Intent intent) {
        Log.d("MainActivity", "Handling notification intent with job_id: " + intent);

        if (intent != null && intent.hasExtra("job_id")) {

            int jobId = intent.getIntExtra("job_id", -1);
            if (jobId > 0) {
                Log.d("MainActivity", "Handling notification intent with job_id: " + jobId);

                if (navController != null) {
                    // Use Navigation Component if available
                    try {
                        Bundle bundle = new Bundle();
                        bundle.putInt("job_id", jobId);
                        navController.navigate(R.id.nav_jobs, bundle);
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error navigating with NavController", e);
                        showJobDetailsFallback(jobId);
                    }
                } else {
                    // Manual navigation
                    showJobDetailsFallback(jobId);
                }
            }
        }
        
        if (intent != null && "saved_jobs".equals(intent.getStringExtra("navigate_to"))) {
            if (navController != null) {
                navController.navigate(R.id.nav_saved_jobs);
            } else {
                loadFragment(new SavedJobsFragment(), "SavedJobs");
            }
        }
    }

    private void showJobDetailsFallback(int jobId) {
        // You can implement a JobDetailsFragment manually here
        //Toast.makeText(this, "Opening job ID: " + jobId, Toast.LENGTH_SHORT).show();
        JobDetailsBottomSheet.newInstance(jobId)
                .show(getSupportFragmentManager(), "JobDetails");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("MainActivity", "onNewIntent called");
        handleNotificationIntent(intent);
        handleDeepLink(intent);
    }

    // ==================== MENU INTEGRATION ====================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        updateNotificationMenuItem(menu);
        return true;
    }

    private void updateNotificationMenuItem(Menu menu) {
        MenuItem notificationItem = menu.findItem(R.id.action_notifications);

        if (notificationItem != null) {
            if (areNotificationsEnabledInApp()) {
                notificationItem.setTitle("Disable Notifications");
                notificationItem.setIcon(R.drawable.ic_notifications_on); // Enabled icon
            } else {
                notificationItem.setTitle("Enable Notifications");
                notificationItem.setIcon(R.drawable.ic_notifications_off); // Disabled icon
            }
        }

        if (notificationItem != null) {
            if (areNotificationsEnabledInApp()) {
                notificationItem.setTitle("Disable Notifications");
            } else {
                notificationItem.setTitle("Enable Notifications");
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_notifications) {
            toggleNotifications();
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_notification_settings) {
            showNotificationSettingsDialog();
            return true;
        } else if (id == R.id.action_settings) {
            Fragment fragment = null;
            String tag = null;

                fragment = new JobsListFragment();
                tag = "JobsList";

                loadFragment(fragment, tag);
            return true;
        }else if (id == R.id.action_share) {
            shareApp();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    private void shareApp() {
        String playStoreUrl = "https://play.google.com/store/apps/details?id=com.solutions.alphil.zambiajobalerts";
        String shareText = "Check out Zambia Job Alerts app for the latest job opportunities in Zambia! Download now: " + playStoreUrl;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share Zambia Job Alerts via"));
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                    || super.onSupportNavigateUp();
        } else {
            if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
                getSupportFragmentManager().popBackStack();
                return true;
            } else {
                return super.onSupportNavigateUp();
            }
        }
    }

    public boolean isNotificationEnabled() {
        return areNotificationsEnabledInApp() && areSystemNotificationsEnabled();
    }

    public void enableNotificationsFromFragment() {
        enableNotifications();
        invalidateOptionsMenu(); // Update menu state
    }

    public void disableNotificationsFromFragment() {
        disableNotifications();
        invalidateOptionsMenu(); // Update menu state
    }


    private void loadRewardedAdRewarded() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, TEST_AD_UNIT_ID_REWARDED, adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        showRewardedAdRewarded();
                        rewardedAd = ad;
                    }
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    }
                });
    }
    private void showRewardedAdRewarded() {

        rewardedAd.show(this, rewardItem -> {
            int rewardAmount = rewardItem.getAmount();
            String rewardType = rewardItem.getType();
        });
    }
}