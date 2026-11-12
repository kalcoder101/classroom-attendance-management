package com.kal.classattendance.data;

import static com.kal.classattendance.data.DatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.kal.classattendance.model.SchoolClass;
import com.kal.classattendance.model.Student;

import java.util.ArrayList;
import java.util.List;

public class StudentDao {

    private final DatabaseHelper helper;

    public StudentDao(Context context) {
        helper = DatabaseHelper.getInstance(context);
    }

    // true if this class already has a student with this code
    public boolean exists(long classId, String studentCode) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_STUDENTS,
                new String[]{COL_ID},
                COL_CLASS_ID + " = ? AND " + COL_STUDENT_CODE + " = ?",
                new String[]{String.valueOf(classId), studentCode},
                null, null, null)) {
            return c.moveToFirst();
        }
    }

    // Saves a new student. Returns the new id, or -1 if it could not be saved.
    public long insert(long classId, String studentCode, String fullName) {
        ContentValues values = new ContentValues();
        values.put(COL_CLASS_ID, classId);
        values.put(COL_STUDENT_CODE, studentCode);
        values.put(COL_FULL_NAME, fullName);
        return helper.getWritableDatabase().insert(TABLE_STUDENTS, null, values);
    }

    // Removes a student. Attendance records referencing them are deleted too (ON DELETE CASCADE).
    public boolean delete(long studentId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(TABLE_STUDENTS, COL_ID + " = ?",
                new String[]{String.valueOf(studentId)}) > 0;
    }

    // All students of this teacher with their class labels, sorted by class then code
    public List<Student> listForTeacher(long teacherId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT s." + COL_ID + ", s." + COL_CLASS_ID + ", s." + COL_STUDENT_CODE
                + ", s." + COL_FULL_NAME
                + ", c." + COL_DEPARTMENT + ", c." + COL_YEAR + ", c." + COL_SECTION
                + " FROM " + TABLE_STUDENTS + " s"
                + " JOIN " + TABLE_CLASSES + " c ON c." + COL_ID + " = s." + COL_CLASS_ID
                + " WHERE c." + COL_TEACHER_ID + " = ?"
                + " ORDER BY c." + COL_DEPARTMENT + ", c." + COL_YEAR + ", c." + COL_SECTION
                + ", s." + COL_STUDENT_CODE;

        List<Student> result = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(teacherId)})) {
            while (c.moveToNext()) {
                result.add(new Student(
                        c.getLong(0),
                        c.getLong(1),
                        c.getString(2),
                        c.getString(3),
                        classLabel(c.getString(4), c.getInt(5), c.getInt(6))));
            }
        }
        return result;
    }

    // Students of one class, sorted by code
    public List<Student> listForClass(long classId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<Student> result = new ArrayList<>();
        try (Cursor c = db.query(TABLE_STUDENTS,
                new String[]{COL_ID, COL_CLASS_ID, COL_STUDENT_CODE, COL_FULL_NAME},
                COL_CLASS_ID + " = ?",
                new String[]{String.valueOf(classId)},
                null, null, COL_STUDENT_CODE)) {
            while (c.moveToNext()) {
                result.add(new Student(
                        c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), null));
            }
        }
        return result;
    }

    // Total students of this teacher (used for dashboard-style counters)
    public int countForTeacher(long teacherId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + TABLE_STUDENTS + " s"
                + " JOIN " + TABLE_CLASSES + " c ON c." + COL_ID + " = s." + COL_CLASS_ID
                + " WHERE c." + COL_TEACHER_ID + " = ?";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(teacherId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** "CS – 4th Year – Section 1" style label, matching SchoolClass.getLabel() */
    static String classLabel(String department, int year, int section) {
        return department + " – " + SchoolClass.yearLabel(year) + " Year – Section " + section;
    }
}
