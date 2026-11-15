package com.kal.classattendance.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Date formats for the keys stored in the database and the labels shown on
 * screen. {@link SimpleDateFormat} is not thread-safe, so call sites get one
 * formatter instance per thread instead of sharing a static one.
 */
public final class Dates {

    private static final ThreadLocal<SimpleDateFormat> DB_KEY =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd", Locale.US));
    private static final ThreadLocal<SimpleDateFormat> DISPLAY =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("d MMM yyyy", Locale.US));
    private static final ThreadLocal<SimpleDateFormat> CHART_LABEL =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("d MMM", Locale.US));

    private Dates() { }

    /** "yyyy-MM-dd" — how dates are stored in the database. */
    public static String dbKey(Date date) {
        return DB_KEY.get().format(date);
    }

    /** "d MMM yyyy" — the full date label shown on screen. */
    public static String display(Date date) {
        return DISPLAY.get().format(date);
    }

    /** "d MMM" — the compact label used on the chart's x-axis. */
    public static String chartLabel(Date date) {
        return CHART_LABEL.get().format(date);
    }

    /** Parses a "yyyy-MM-dd" database key; null when the text is not a valid key. */
    public static Date parseDbKey(String text) {
        try {
            return DB_KEY.get().parse(text);
        } catch (ParseException e) {
            return null;
        }
    }
}
