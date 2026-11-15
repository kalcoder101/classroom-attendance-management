package com.kal.classattendance.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers the logged-in teacher between launches, so returning users skip
 * the login screen. Only the id is kept; the name is read from the database.
 */
public final class SessionStore {

    private static final String PREFS = "session";
    private static final String KEY_TEACHER_ID = "teacher_id";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Marks this teacher as logged in from now on. */
    public void save(long teacherId) {
        prefs.edit().putLong(KEY_TEACHER_ID, teacherId).apply();
    }

    /** Saved teacher id, or -1 when nobody is remembered. */
    public long getTeacherId() {
        return prefs.getLong(KEY_TEACHER_ID, -1);
    }

    public void clear() {
        prefs.edit().remove(KEY_TEACHER_ID).apply();
    }
}
