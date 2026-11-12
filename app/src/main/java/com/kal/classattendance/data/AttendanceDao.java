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

    // ==================== Reports & Analytics Support ====================

    public static class SessionSummary {
        private final long sessionId;
        private final long classId;
        private final String classLabel;
        private final String sessionDate;
        private final int presentCount;
        private final int absentCount;
        private final int lateCount;
        private final int totalStudents;

        public SessionSummary(long sessionId, long classId, String classLabel,
                              String sessionDate, int presentCount, int absentCount,
                              int lateCount, int totalStudents) {
            this.sessionId = sessionId;
            this.classId = classId;
            this.classLabel = classLabel;
            this.sessionDate = sessionDate;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.lateCount = lateCount;
            this.totalStudents = totalStudents;
        }

        public long getSessionId() { return sessionId; }
        public long getClassId() { return classId; }
        public String getClassLabel() { return classLabel; }
        public String getSessionDate() { return sessionDate; }
        public int getPresentCount() { return presentCount; }
        public int getAbsentCount() { return absentCount; }
        public int getLateCount() { return lateCount; }
        public int getTotalStudents() { return totalStudents; }

        public float getAttendanceRate() {
            if (totalStudents == 0) return 0f;
            return ((float) presentCount / totalStudents) * 100f;
        }
    }

    public static class AttendanceOverviewStats {
        public final int totalSessions;
        public final int totalPresent;
        public final int totalAbsent;
        public final int totalLate;
        public final int totalRecords;
        public final float attendanceRate;

        public AttendanceOverviewStats(int totalSessions, int totalPresent, int totalAbsent, int totalLate) {
            this.totalSessions = totalSessions;
            this.totalPresent = totalPresent;
            this.totalAbsent = totalAbsent;
            this.totalLate = lateCountClean(totalLate);
            this.totalRecords = totalPresent + totalAbsent + this.totalLate;
            this.attendanceRate = totalRecords > 0 ? ((float) totalPresent / totalRecords) * 100f : 0f;
        }

        private static int lateCountClean(int late) {
            return Math.max(0, late);
        }
    }

    public static class StudentAttendanceRecord {
        public final long studentId;
        public final String studentCode;
        public final String fullName;
        public final String status;

        public StudentAttendanceRecord(long studentId, String studentCode, String fullName, String status) {
            this.studentId = studentId;
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.status = status;
        }
    }

    public static class StudentRosterStat {
        public final long studentId;
        public final String studentCode;
        public final String fullName;
        public final String classLabel;
        public final int presentCount;
        public final int absentCount;
        public final int lateCount;
        public final int totalSessions;
        public final float attendanceRate;

        public StudentRosterStat(long studentId, String studentCode, String fullName,
                                 String classLabel, int presentCount, int absentCount,
                                 int lateCount) {
            this.studentId = studentId;
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.classLabel = classLabel;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.lateCount = lateCount;
            this.totalSessions = presentCount + absentCount + lateCount;
            this.attendanceRate = totalSessions > 0 ? ((float) presentCount / totalSessions) * 100f : 0f;
        }

        public boolean isAtRisk() {
            return totalSessions > 0 && attendanceRate < 75.0f;
        }
    }

    /** List of session summaries for a teacher, optionally filtered by classId (-1 for all). */
    public java.util.List<SessionSummary> getSessionSummaries(long teacherId, long classIdFilter) {
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.").append(COL_ID).append(" AS session_id, ")
           .append("s.").append(COL_CLASS_ID).append(", ")
           .append("c.").append(COL_DEPARTMENT).append(", ")
           .append("c.").append(COL_YEAR).append(", ")
           .append("c.").append(COL_SECTION).append(", ")
           .append("s.").append(COL_SESSION_DATE).append(", ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'P' THEN 1 ELSE 0 END) AS count_p, ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'A' THEN 1 ELSE 0 END) AS count_a, ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'L' THEN 1 ELSE 0 END) AS count_l, ")
           .append("COUNT(r.").append(COL_ID).append(") AS count_total ")
           .append("FROM ").append(TABLE_SESSIONS).append(" s ")
           .append("JOIN ").append(TABLE_CLASSES).append(" c ON c.").append(COL_ID).append(" = s.").append(COL_CLASS_ID).append(" ")
           .append("LEFT JOIN ").append(TABLE_RECORDS).append(" r ON r.").append(COL_SESSION_ID).append(" = s.").append(COL_ID).append(" ")
           .append("WHERE c.").append(COL_TEACHER_ID).append(" = ? ");

        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(String.valueOf(teacherId));

        if (classIdFilter != -1) {
            sql.append("AND c.").append(COL_ID).append(" = ? ");
            args.add(String.valueOf(classIdFilter));
        }

        sql.append("GROUP BY s.").append(COL_ID).append(" ")
           .append("ORDER BY s.").append(COL_SESSION_DATE).append(" DESC, s.").append(COL_ID).append(" DESC");

        java.util.List<SessionSummary> list = new java.util.ArrayList<>();
        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                long sessionId = c.getLong(c.getColumnIndexOrThrow("session_id"));
                long classId = c.getLong(c.getColumnIndexOrThrow(COL_CLASS_ID));
                String dept = c.getString(c.getColumnIndexOrThrow(COL_DEPARTMENT));
                int year = c.getInt(c.getColumnIndexOrThrow(COL_YEAR));
                int section = c.getInt(c.getColumnIndexOrThrow(COL_SECTION));
                String date = c.getString(c.getColumnIndexOrThrow(COL_SESSION_DATE));
                int p = c.getInt(c.getColumnIndexOrThrow("count_p"));
                int a = c.getInt(c.getColumnIndexOrThrow("count_a"));
                int l = c.getInt(c.getColumnIndexOrThrow("count_l"));
                int total = c.getInt(c.getColumnIndexOrThrow("count_total"));

                String label = dept + " – " + com.kal.classattendance.model.SchoolClass.yearLabel(year)
                        + " Year – Section " + section;

                list.add(new SessionSummary(sessionId, classId, label, date, p, a, l, total));
            }
        }
        return list;
    }

    /** Compute overall aggregate statistics for a teacher's classes. */
    public AttendanceOverviewStats getOverviewStats(long teacherId, long classIdFilter) {
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("COUNT(DISTINCT s.").append(COL_ID).append(") AS total_sessions, ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'P' THEN 1 ELSE 0 END) AS count_p, ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'A' THEN 1 ELSE 0 END) AS count_a, ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'L' THEN 1 ELSE 0 END) AS count_l ")
           .append("FROM ").append(TABLE_SESSIONS).append(" s ")
           .append("JOIN ").append(TABLE_CLASSES).append(" c ON c.").append(COL_ID).append(" = s.").append(COL_CLASS_ID).append(" ")
           .append("LEFT JOIN ").append(TABLE_RECORDS).append(" r ON r.").append(COL_SESSION_ID).append(" = s.").append(COL_ID).append(" ")
           .append("WHERE c.").append(COL_TEACHER_ID).append(" = ? ");

        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(String.valueOf(teacherId));

        if (classIdFilter != -1) {
            sql.append("AND c.").append(COL_ID).append(" = ? ");
            args.add(String.valueOf(classIdFilter));
        }

        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            if (c.moveToNext()) {
                int sessions = c.getInt(c.getColumnIndexOrThrow("total_sessions"));
                int p = c.getInt(c.getColumnIndexOrThrow("count_p"));
                int a = c.getInt(c.getColumnIndexOrThrow("count_a"));
                int l = c.getInt(c.getColumnIndexOrThrow("count_l"));
                return new AttendanceOverviewStats(sessions, p, a, l);
            }
        }
        return new AttendanceOverviewStats(0, 0, 0, 0);
    }

    /** Returns the most recent attendance session recorded by this teacher. */
    public SessionSummary getLatestSessionForTeacher(long teacherId) {
        java.util.List<SessionSummary> list = getSessionSummaries(teacherId, -1);
        return list.isEmpty() ? null : list.get(0);
    }

    /** List student attendance details for a given session. */
    public java.util.List<StudentAttendanceRecord> getSessionDetails(long sessionId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT st." + COL_ID + ", st." + COL_STUDENT_CODE + ", st." + COL_FULL_NAME
                + ", r." + COL_STATUS
                + " FROM " + TABLE_RECORDS + " r"
                + " JOIN " + TABLE_STUDENTS + " st ON st." + COL_ID + " = r." + COL_STUDENT_ID
                + " WHERE r." + COL_SESSION_ID + " = ?"
                + " ORDER BY st." + COL_FULL_NAME + " ASC";

        java.util.List<StudentAttendanceRecord> records = new java.util.ArrayList<>();
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(sessionId)})) {
            while (c.moveToNext()) {
                records.add(new StudentAttendanceRecord(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3)
                ));
            }
        }
        return records;
    }

    /**
     * Aggregates attendance statistics per student across all sessions for the given teacher/class.
     */
    public java.util.List<StudentRosterStat> getStudentRosterStats(long teacherId, long classIdFilter) {
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT st.").append(COL_ID).append(", ")
           .append("st.").append(COL_STUDENT_CODE).append(", ")
           .append("st.").append(COL_FULL_NAME).append(", ")
           .append("c.").append(COL_DEPARTMENT).append(", ")
           .append("c.").append(COL_YEAR).append(", ")
           .append("c.").append(COL_SECTION).append(", ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'P' THEN 1 ELSE 0 END) AS count_p, ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'A' THEN 1 ELSE 0 END) AS count_a, ")
           .append("SUM(CASE WHEN r.").append(COL_STATUS).append(" = 'L' THEN 1 ELSE 0 END) AS count_l ")
           .append("FROM ").append(TABLE_STUDENTS).append(" st ")
           .append("JOIN ").append(TABLE_CLASSES).append(" c ON c.").append(COL_ID).append(" = st.").append(COL_CLASS_ID).append(" ")
           .append("LEFT JOIN ").append(TABLE_RECORDS).append(" r ON r.").append(COL_STUDENT_ID).append(" = st.").append(COL_ID).append(" ")
           .append("WHERE c.").append(COL_TEACHER_ID).append(" = ? ");

        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(String.valueOf(teacherId));

        if (classIdFilter != -1) {
            sql.append("AND c.").append(COL_ID).append(" = ? ");
            args.add(String.valueOf(classIdFilter));
        }

        sql.append("GROUP BY st.").append(COL_ID).append(" ")
           .append("ORDER BY st.").append(COL_STUDENT_CODE).append(" ASC, st.").append(COL_FULL_NAME).append(" ASC");

        java.util.List<StudentRosterStat> list = new java.util.ArrayList<>();
        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                long studentId = c.getLong(0);
                String code = c.getString(1);
                String name = c.getString(2);
                String dept = c.getString(3);
                int year = c.getInt(4);
                int section = c.getInt(5);
                int p = c.getInt(6);
                int a = c.getInt(7);
                int l = c.getInt(8);

                String label = dept + " – " + com.kal.classattendance.model.SchoolClass.yearLabel(year)
                        + " Year – Section " + section;

                list.add(new StudentRosterStat(studentId, code, name, label, p, a, l));
            }
        }
        return list;
    }
}
