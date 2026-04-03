package com.solutions.alphil.zambiajobalerts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.solutions.alphil.zambiajobalerts.MainActivity;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMsgService";
    private static final String CHANNEL_ID = "job_alerts_channel";
    private static final String CHANNEL_NAME = "Job Alerts";
    private static final String PREFS_NAME = "app_prefs";
    private static final String NOTIFICATIONS_ENABLED = "notifications_enabled";

    // WordPress Credentials Sync
    private static final String WP_PREF_NAME = "wp_creds";
    private static final String KEY_WP_PWD = "wp_password";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();

        // Check for remote password update command
        if (data.containsKey("new_wp_password")) {
            String newPwd = data.get("new_wp_password");
            updateWpPassword(newPwd);
            // If it's just a config update, we might not want to show a notification
            if (!data.containsKey("message") && remoteMessage.getNotification() == null) {
                return;
            }
        }

        // Check if notifications are enabled by user
        if (!areNotificationsEnabled()) {
            Log.d(TAG, "Notifications disabled by user, skipping...");
            return;
        }

        if (!data.isEmpty()) {
            Log.d(TAG, "Message data payload: " + data);

            String jobId = data.get("job_id");
            String type = data.get("type");
            String title = data.get("title");
            String message = data.get("message");
            String company = data.get("company");
            String location = data.get("location");

            if (title == null && remoteMessage.getNotification() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (message == null && remoteMessage.getNotification() != null) {
                message = remoteMessage.getNotification().getBody();
            }

            if (title == null) title = "New Job Alert!";
            if (message == null) message = "Tap to view job details";

            showNotification(title, message, jobId, type, company, location);
        } else if (remoteMessage.getNotification() != null) {
            showNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody(),
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private void updateWpPassword(String newPwd) {
        if (newPwd != null && !newPwd.isEmpty()) {
            SharedPreferences wpPrefs = getSharedPreferences(WP_PREF_NAME, Context.MODE_PRIVATE);
            wpPrefs.edit().putString(KEY_WP_PWD, newPwd).apply();
            Log.d(TAG, "WordPress password updated remotely via FCM");
        }
    }

    private void showNotification(String title, String body, String jobId, String type, String company, String location) {
        try {
            createNotificationChannel();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (jobId != null) {
                try {
                    intent.putExtra("job_id", Integer.parseInt(jobId));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid job ID: " + jobId);
                }
            }
            if (type != null) {
                try {
                    intent.putExtra("type", Integer.parseInt(type));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid type: " + type);
                }
            }

            int requestCode = (int) System.currentTimeMillis();
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL);

            if (body.length() > 50 || company != null || location != null) {
                NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
                StringBuilder bigText = new StringBuilder(body);

                if (company != null) {
                    bigText.append("\n\nCompany: ").append(company);
                }
                if (location != null) {
                    bigText.append("\nLocation: ").append(location);
                }

                bigTextStyle.bigText(bigText.toString());
                builder.setStyle(bigTextStyle);
            }

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                int notificationId = (jobId != null) ? Integer.parseInt(jobId) : requestCode;
                manager.notify(notificationId, builder.build());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new job postings and updates");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private boolean areNotificationsEnabled() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean userEnabled = prefs.getBoolean(NOTIFICATIONS_ENABLED, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                return userEnabled && manager.areNotificationsEnabled();
            }
        }

        return userEnabled;
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
        subscribeToJobTopics();
    }

    private void subscribeToJobTopics() {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_jobs");
            FirebaseMessaging.getInstance().subscribeToTopic("new_jobs");
        } catch (Exception e) {
            Log.e(TAG, "Error subscribing to topics", e);
        }
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public static boolean isNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(NOTIFICATIONS_ENABLED, true);
    }
}