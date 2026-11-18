package com.kal.classattendance.util;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the recurring notification work. WorkManager is used instead of
 * {@code AlarmManager} so the reminders survive reboots and Doze without an
 * extra boot receiver or exact-alarm permission.
 */
public final class ReminderScheduler {

    private static final String WORK_REMINDER = "attendance_reminder_daily";
    private static final String WORK_DIGEST = "attendance_weekly_digest";

    // The weekly digest goes out Monday morning.
    private static final int DIGEST_DAY_OF_WEEK = Calendar.MONDAY;
    private static final int DIGEST_HOUR = 9;
    private static final int DIGEST_MINUTE = 0;

    private ReminderScheduler() {}

    /** Re-applies the user's current settings. Safe to call on every dashboard open. */
    public static void scheduleAll(Context context) {
        NotificationPrefs prefs = new NotificationPrefs(context);

        if (prefs.isReminderEnabled()) {
            scheduleDailyReminder(context, prefs.reminderHour(), prefs.reminderMinute());
        } else {
            cancelReminder(context);
        }

        if (prefs.isDigestEnabled()) {
            scheduleWeeklyDigest(context);
        } else {
            cancelDigest(context);
        }
    }

    public static void scheduleDailyReminder(Context context, int hour, int minute) {
        WorkManager workManager = WorkManager.getInstance(context);
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AttendanceReminderWorker.class, 1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNext(hour, minute), TimeUnit.MILLISECONDS)
                .addTag(WORK_REMINDER)
                .build();
        workManager.enqueueUniquePeriodicWork(WORK_REMINDER,
                ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancelReminder(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_REMINDER);
    }

    public static void scheduleWeeklyDigest(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WeeklyDigestWorker.class, 7, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNext(DIGEST_DAY_OF_WEEK, DIGEST_HOUR, DIGEST_MINUTE),
                        TimeUnit.MILLISECONDS)
                .addTag(WORK_DIGEST)
                .build();
        workManager.enqueueUniquePeriodicWork(WORK_DIGEST,
                ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancelDigest(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DIGEST);
    }

    /** Millis from now until the next occurrence of {@code hour:minute}. */
    static long delayUntilNext(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    /** Millis from now until the next occurrence of the given weekday and time. */
    static long delayUntilNext(int dayOfWeek, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (!target.after(now)) {
            target.add(Calendar.WEEK_OF_YEAR, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }
}
