package com.kal.classattendance.util;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

import com.kal.classattendance.LoginActivity;
import com.kal.classattendance.util.SessionStore;

/**
 * Shared logout behaviour so every screen that offers "Logout"
 * (dashboard, class list, attendance overflow menu) acts the same way.
 */
public final class Logout {

    private Logout() { }

    /** Clears the remembered session and returns to LoginActivity, clearing the whole back stack. */
    public static void go(AppCompatActivity activity) {
        new SessionStore(activity).clear();
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
