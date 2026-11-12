package com.kal.classattendance.data;

import static com.kal.classattendance.data.DatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes attendance. A "session" is one class on one date;
 * each session holds one status record (P / A / L) per student.
 */
public class AttendanceDao {

    // Status values stored in the database (CHECK constraint allows only these)
    public static final String STATUS_PRESENT = "P";
    public static final String STATUS_ABSENT = "A";
    public static final String STATUS_LATE = "L";

    private final DatabaseHelper helper;

    public AttendanceDao(Context context) {
        helper = DatabaseHelper.getInstance(context);
    }

    /** _id of the session for this class + date, created if it does not exist yet. */
    public long getOrCreateSession(long classId, String sessionDate) {
        SQLiteDatabase db = helper.getWritableDatabase();

        long existing = findSessionId(db, classId, sessionDate);
        if (existing != -1) {
            return existing;
        }

        ContentValues values = new ContentValues();
        values.put(COL_CLASS_ID, classId);
        values.put(COL_SESSION_DATE, sessionDate);
        return db.insert(TABLE_SESSIONS, null, values);
    }

    private long findSessionId(SQLiteDatabase db, long classId, String sessionDate) {
        try (Cursor c = db.query(TABLE_SESSIONS,
                new String[]{COL_ID},
                COL_CLASS_ID + " = ? AND " + COL_SESSION_DATE + " = ?",
                new String[]{String.valueOf(classId), sessionDate},
                null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    /**
     * Saves one status per student in a single transaction. Records already
     * stored for this session are replaced (upsert), so saving the same
     * session twice simply updates it.
     */
    public boolean saveRecords(long sessionId, Map<Long, String> statuses) {
        if (statuses.isEmpty()) {
            return false;
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map.Entry<Long, String> entry : statuses.entrySet()) {
                ContentValues values = new ContentValues();
                values.put(COL_SESSION_ID, sessionId);
                values.put(COL_STUDENT_ID, entry.getKey());
                values.put(COL_STATUS, entry.getValue());
                db.insertWithOnConflict(TABLE_RECORDS, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** Saved statuses for this session: student_id -> "P" / "A" / "L". */
    public Map<Long, String> loadRecords(long sessionId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Map<Long, String> result = new HashMap<>();
        try (Cursor c = db.query(TABLE_RECORDS,
                new String[]{COL_STUDENT_ID, COL_STATUS},
                COL_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)},
                null, null, null)) {
            while (c.moveToNext()) {
                result.put(c.getLong(0), c.getString(1));
            }
        }
        return result;
    }

    /** true when this class already has attendance saved for the given date. */
    public boolean hasSession(long classId, String sessionDate) {
        return findSessionId(helper.getReadableDatabase(), classId, sessionDate) != -1;
    }
}
