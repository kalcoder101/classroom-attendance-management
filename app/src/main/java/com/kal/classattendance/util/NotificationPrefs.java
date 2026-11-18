package com.kal.classattendance.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small preferences store for the notification features: whether the daily
 * attendance reminder and weekly digest are on, and what time the reminder
 * should fire. Kept separate from {@link SessionStore} so notification settings
 * are not wiped on logout.
 */
public final class NotificationPrefs {

    private static final String PREFS = "notification_prefs";

    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_DIGEST_ENABLED = "digest_enabled";

    /** Default reminder time: 5:00 PM, after a typical school day. */
    public static final int DEFAULT_HOUR = 17;
    public static final int DEFAULT_MINUTE = 0;

    private final SharedPreferences prefs;

    public NotificationPrefs(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isReminderEnabled() {
        return prefs.getBoolean(KEY_REMINDER_ENABLED, true);
    }

    public void setReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply();
    }

    public int reminderHour() {
        return prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR);
    }

    public int reminderMinute() {
        return prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_MINUTE);
    }

    public void setReminderTime(int hour, int minute) {
        prefs.edit()
                .putInt(KEY_REMINDER_HOUR, hour)
                .putInt(KEY_REMINDER_MINUTE, minute)
                .apply();
    }

    public boolean isDigestEnabled() {
        return prefs.getBoolean(KEY_DIGEST_ENABLED, true);
    }

    public void setDigestEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DIGEST_ENABLED, enabled).apply();
    }
}
