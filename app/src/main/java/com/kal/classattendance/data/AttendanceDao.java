package com.kal.classattendance.data;

import static com.kal.classattendance.data.DatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Copies a student's marks from sessions of their old class dated on or
     * after {@code fromDate} into the matching sessions of the new class,
     * creating those sessions when needed. Marks the new class already has for
     * that date are left alone. Returns how many marks were copied.
     */
    public int copyRecordsFromDate(long studentId, long oldClassId, long newClassId, String fromDate) {
        SQLiteDatabase db = helper.getWritableDatabase();

        // date -> status for the marks that should follow the student
        Map<String, String> marksByDate = new LinkedHashMap<>();
        String sql = "SELECT s." + COL_SESSION_DATE + ", r." + COL_STATUS
                + " FROM " + TABLE_RECORDS + " r"
                + " JOIN " + TABLE_SESSIONS + " s ON s." + COL_ID + " = r." + COL_SESSION_ID
                + " WHERE r." + COL_STUDENT_ID + " = ?"
                + " AND s." + COL_CLASS_ID + " = ?"
                + " AND s." + COL_SESSION_DATE + " >= ?";
        try (Cursor c = db.rawQuery(sql, new String[]{
                String.valueOf(studentId), String.valueOf(oldClassId), fromDate})) {
            while (c.moveToNext()) {
                marksByDate.put(c.getString(0), c.getString(1));
            }
        }
        if (marksByDate.isEmpty()) {
            return 0;
        }

        int copied = 0;
        db.beginTransaction();
        try {
            for (Map.Entry<String, String> entry : marksByDate.entrySet()) {
                long sessionId = getOrCreateSession(newClassId, entry.getKey());
                if (sessionId == -1) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(COL_SESSION_ID, sessionId);
                values.put(COL_STUDENT_ID, studentId);
                values.put(COL_STATUS, entry.getValue());
                long row = db.insertWithOnConflict(TABLE_RECORDS, null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (row != -1) {
                    copied++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return copied;
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

    /**
     * _id of the session saved for this class + date, or -1 when nothing has
     * been saved yet. Read-only: merely viewing a date must not create a row.
     */
    public long findSessionId(long classId, String sessionDate) {
        return findSessionId(helper.getReadableDatabase(), classId, sessionDate);
    }

    // ==================== Attendance Rule ====================

    /**
     * The single attendance-rate rule: a student attended when they were
     * present or late, and only absences count against them. Everything that
     * shows or judges a rate goes through this method.
     */
    public static float attendanceRate(int present, int late, int total) {
        if (total <= 0) {
            return 0f;
        }
        return ((float) (present + late) / total) * 100f;
    }

    /** Below this rate a student is reported as at risk (in percent). */
    public static final float AT_RISK_THRESHOLD = 75f;

    public static boolean isBelowAtRiskThreshold(int present, int late, int total) {
        return total > 0 && attendanceRate(present, late, total) < AT_RISK_THRESHOLD;
    }

    // ==================== Reports & Analytics Support ====================

    public static class SessionSummary {
        private final long classId;
        private final String classLabel;
        private final String sessionDate;
        private final int presentCount;
        private final int absentCount;
        private final int lateCount;
        private final int totalStudents;

        public SessionSummary(long classId, String classLabel,
                              String sessionDate, int presentCount, int absentCount,
                              int lateCount, int totalStudents) {
            this.classId = classId;
            this.classLabel = classLabel;
            this.sessionDate = sessionDate;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.lateCount = lateCount;
            this.totalStudents = totalStudents;
        }

        public long getClassId() { return classId; }
        public String getClassLabel() { return classLabel; }
        public String getSessionDate() { return sessionDate; }
        public int getPresentCount() { return presentCount; }
        public int getAbsentCount() { return absentCount; }
        public int getLateCount() { return lateCount; }

        public float getAttendanceRate() {
            return AttendanceDao.attendanceRate(presentCount, lateCount, totalStudents);
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
            this.totalLate = Math.max(0, totalLate);
            this.totalRecords = totalPresent + totalAbsent + this.totalLate;
            this.attendanceRate = AttendanceDao.attendanceRate(totalPresent, this.totalLate, totalRecords);
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
            this.attendanceRate = AttendanceDao.attendanceRate(presentCount, lateCount, totalSessions);
        }

        public boolean isAtRisk() {
            return AttendanceDao.isBelowAtRiskThreshold(presentCount, lateCount, totalSessions);
        }
    }

    /** List of session summaries for a teacher, optionally filtered by classId (-1 for all). */
    public java.util.List<SessionSummary> getSessionSummaries(long teacherId, long classIdFilter) {
        return querySessionSummaries(teacherId, classIdFilter, -1);
    }

    /** Shared session query; a positive {@code limit} keeps only the newest rows. */
    private java.util.List<SessionSummary> querySessionSummaries(
            long teacherId, long classIdFilter, int limit) {
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.").append(COL_CLASS_ID).append(", ")
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
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }

        java.util.List<SessionSummary> list = new java.util.ArrayList<>();
        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
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

                list.add(new SessionSummary(classId, label, date, p, a, l, total));
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
        java.util.List<SessionSummary> list = querySessionSummaries(teacherId, -1, 1);
        return list.isEmpty() ? null : list.get(0);
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
           // A moved student's marks belong to the class of their session, so
           // only sessions of their current class are counted here; marks from
           // the old class stay in that class's records and reports.
           .append("SUM(CASE WHEN s.").append(COL_CLASS_ID).append(" = st.").append(COL_CLASS_ID)
                   .append(" AND r.").append(COL_STATUS).append(" = 'P' THEN 1 ELSE 0 END) AS count_p, ")
           .append("SUM(CASE WHEN s.").append(COL_CLASS_ID).append(" = st.").append(COL_CLASS_ID)
                   .append(" AND r.").append(COL_STATUS).append(" = 'A' THEN 1 ELSE 0 END) AS count_a, ")
           .append("SUM(CASE WHEN s.").append(COL_CLASS_ID).append(" = st.").append(COL_CLASS_ID)
                   .append(" AND r.").append(COL_STATUS).append(" = 'L' THEN 1 ELSE 0 END) AS count_l ")
           .append("FROM ").append(TABLE_STUDENTS).append(" st ")
           .append("JOIN ").append(TABLE_CLASSES).append(" c ON c.").append(COL_ID).append(" = st.").append(COL_CLASS_ID).append(" ")
           .append("LEFT JOIN ").append(TABLE_RECORDS).append(" r ON r.").append(COL_STUDENT_ID).append(" = st.").append(COL_ID).append(" ")
           .append("LEFT JOIN ").append(TABLE_SESSIONS).append(" s ON s.").append(COL_ID).append(" = r.").append(COL_SESSION_ID).append(" ")
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
