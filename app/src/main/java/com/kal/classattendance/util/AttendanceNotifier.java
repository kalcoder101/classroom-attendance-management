package com.kal.classattendance.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.kal.classattendance.R;
import com.kal.classattendance.ReportsActivity;

/**
 * Notification channel and the "attendance saved" summary notification.
 * Tapping the notification opens the report for that class.
 */
public final class AttendanceNotifier {

    public static final String CHANNEL_ID = "attendance_saved";
    private static final int NOTIFICATION_ID = 1001;

    private AttendanceNotifier() {}

    /** Creates the channel; safe to call before every post. */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    /** true when the app is currently allowed to post notifications. */
    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Posts "Class: 28 P, 2 A, 1 L" for the session that was just saved. */
    public static void notifySaved(Context context, long teacherId, long classId,
                                   String classLabel, int present, int absent, int late) {
        ensureChannel(context);
        if (!canPost(context)) {
            return;
        }

        Intent intent = new Intent(context, ReportsActivity.class);
        intent.putExtra(ReportsActivity.EXTRA_TEACHER_ID, teacherId);
        intent.putExtra(ReportsActivity.EXTRA_CLASS_ID, classId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String summary = context.getString(R.string.attendance_saved_summary,
                classLabel, present, absent, late);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_take_attendance)
                .setContentTitle(context.getString(R.string.attendance_saved_title))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Permission was revoked between the check and the post
        }
    }
}
