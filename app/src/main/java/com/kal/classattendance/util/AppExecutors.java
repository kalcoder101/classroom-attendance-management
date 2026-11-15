package com.kal.classattendance.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared background executor so slow work (PBKDF2 password hashing above all)
 * never blocks the UI thread. Results are posted back on the main thread.
 */
public final class AppExecutors {

    private static final ExecutorService DB = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() { }

    /** Runs a task on a background worker. */
    public static void runOnDb(Runnable task) {
        DB.execute(task);
    }

    /** Runs a task on the main thread, safe to call from any worker. */
    public static void runOnMain(Runnable task) {
        MAIN.post(task);
    }
}
