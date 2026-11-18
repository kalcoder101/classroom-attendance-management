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

import com.kal.classattendance.ClassListActivity;
import com.kal.classattendance.R;
import com.kal.classattendance.ReportsActivity;
import com.kal.classattendance.model.SchoolClass;

import java.util.List;

/**
 * Notification channels and the notifications posted by the app.
 * <ul>
 *     <li>"attendance saved" — confirmation after a session is saved</li>
 *     <li>"attendance reminders" — a daily nudge for classes with no marks today</li>
 *     <li>"low attendance alerts" — students who fell below the at-risk threshold</li>
 *     <li>"weekly summary" — one low-priority digest per week</li>
 * </ul>
 * Tapping a notification opens the most relevant screen for it.
 */
public final class AttendanceNotifier {

    /** Confirmation posted right after attendance is saved. */
    public static final String CHANNEL_SAVED = "attendance_saved";
    /** Daily reminder for classes still missing attendance. */
    public static final String CHANNEL_REMINDERS = "attendance_reminders";
    /** Alert when students fall below the at-risk attendance threshold. */
    public static final String CHANNEL_AT_RISK = "low_attendance";
    /** Weekly low-importance summary. */
    public static final String CHANNEL_DIGEST = "weekly_digest";

    // Keep the historical name so existing calls keep working.
    public static final String CHANNEL_ID = CHANNEL_SAVED;

    private static final int NOTIFICATION_ID = 1001;
    private static final int REMINDER_NOTIFICATION_ID = 2001;
    private static final int AT_RISK_NOTIFICATION_BASE = 3000;
    private static final int DIGEST_NOTIFICATION_ID = 4001;

    private AttendanceNotifier() {}

    /** Creates every channel; safe to call before each post. */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        createChannel(context, manager, CHANNEL_SAVED,
                R.string.notification_channel_name,
                R.string.notification_channel_description,
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(context, manager, CHANNEL_REMINDERS,
                R.string.reminder_channel_name,
                R.string.reminder_channel_description,
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(context, manager, CHANNEL_AT_RISK,
                R.string.at_risk_channel_name,
                R.string.at_risk_channel_description,
                NotificationManager.IMPORTANCE_DEFAULT);
        // The digest is informational, so it stays silent.
        createChannel(context, manager, CHANNEL_DIGEST,
                R.string.digest_channel_name,
                R.string.digest_channel_description,
                NotificationManager.IMPORTANCE_LOW);
    }

    private static void createChannel(Context context, NotificationManager manager, String id,
                                      int nameRes, int descriptionRes, int importance) {
        NotificationChannel channel = new NotificationChannel(
                id, context.getString(nameRes), importance);
        channel.setDescription(context.getString(descriptionRes));
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

    /**
     * Reminds the teacher that the given classes still have no session today.
     * Tapping opens the class list in attendance mode.
     */
    public static void notifyMissingAttendance(Context context, long teacherId,
                                               List<SchoolClass> missing) {
        if (missing == null || missing.isEmpty()) {
            return;
        }
        ensureChannel(context);
        if (!canPost(context)) {
            return;
        }

        Intent intent = new Intent(context, ClassListActivity.class);
        intent.putExtra(com.kal.classattendance.LoginActivity.EXTRA_TEACHER_ID, teacherId);
        intent.putExtra(ClassListActivity.EXTRA_MODE, ClassListActivity.MODE_ATTENDANCE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(context, REMINDER_NOTIFICATION_ID,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body;
        if (missing.size() == 1) {
            body = context.getString(R.string.reminder_body_single, missing.get(0).getLabel());
        } else {
            body = context.getResources().getQuantityString(
                    R.plurals.reminder_body, missing.size(), missing.size());
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_take_attendance)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);

        try {
            NotificationManagerCompat.from(context)
                    .notify(REMINDER_NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Permission was revoked between the check and the post
        }
    }

    /**
     * Alerts the teacher that {@code atRiskCount} students in this class dropped
     * below the at-risk threshold. Tapping opens the class report.
     */
    public static void notifyAtRisk(Context context, long teacherId, long classId,
                                    String classLabel, int atRiskCount) {
        if (atRiskCount <= 0) {
            return;
        }
        ensureChannel(context);
        if (!canPost(context)) {
            return;
        }

        Intent intent = new Intent(context, ReportsActivity.class);
        intent.putExtra(ReportsActivity.EXTRA_TEACHER_ID, teacherId);
        intent.putExtra(ReportsActivity.EXTRA_CLASS_ID, classId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int notificationId = AT_RISK_NOTIFICATION_BASE + (int) (classId % 1000);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = context.getResources().getQuantityString(
                R.plurals.at_risk_body, atRiskCount, atRiskCount);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_AT_RISK)
                .setSmallIcon(R.drawable.ic_reports)
                .setContentTitle(context.getString(R.string.at_risk_title, classLabel))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS);

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException ignored) {
            // Permission was revoked between the check and the post
        }
    }

    /**
     * Posts the weekly summary. Tapping opens the reports screen for all classes.
     */
    public static void notifyDigest(Context context, long teacherId,
                                    int sessions, int averageRate, int atRiskCount) {
        ensureChannel(context);
        if (!canPost(context)) {
            return;
        }

        Intent intent = new Intent(context, ReportsActivity.class);
        intent.putExtra(ReportsActivity.EXTRA_TEACHER_ID, teacherId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(context, DIGEST_NOTIFICATION_ID,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = context.getString(R.string.digest_body, sessions, averageRate, atRiskCount);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DIGEST)
                .setSmallIcon(R.drawable.ic_reports)
                .setContentTitle(context.getString(R.string.digest_title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS);

        try {
            NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Permission was revoked between the check and the post
        }
    }
}
