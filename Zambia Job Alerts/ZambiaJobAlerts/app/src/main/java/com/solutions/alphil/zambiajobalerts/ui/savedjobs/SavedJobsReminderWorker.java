package com.solutions.alphil.zambiajobalerts.ui.savedjobs;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.solutions.alphil.zambiajobalerts.MainActivity;
import com.solutions.alphil.zambiajobalerts.R;

public class SavedJobsReminderWorker extends Worker {

    private static final String PREFS_NAME = "saved_jobs_prefs";
    private static final String KEY_SAVED_JOBS = "saved_jobs_list";
    private static final String CHANNEL_ID = "saved_jobs_reminder_channel";

    public SavedJobsReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedJobsJson = prefs.getString(KEY_SAVED_JOBS, null);

        // Only remind if there are saved jobs (check if JSON is not null and not empty array "[]")
        if (savedJobsJson != null && !savedJobsJson.equals("[]")) {
            showNotification(context);
        }

        return Result.success();
    }

    private void showNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Saved Jobs Reminder",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Reminders for your saved jobs");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("navigate_to", "saved_jobs");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Zambia Job Alerts")
                .setContentText("You have saved jobs! Look at them and apply now.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(1001, builder.build());
        }
    }
}
