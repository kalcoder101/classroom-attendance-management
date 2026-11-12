package com.kal.classattendance.data;

import static com.kal.classattendance.data.DatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.kal.classattendance.model.SchoolClass;

import java.util.ArrayList;
import java.util.List;

public class ClassDao {

    private final DatabaseHelper helper;

    public ClassDao(Context context) {
        helper = DatabaseHelper.getInstance(context);
    }

    // true if this teacher already has a class with the same department/year/section
    public boolean exists(long teacherId, String department, int year, int section) {
        return existsExcept(teacherId, department, year, section, -1);
    }

    // true if another class of this teacher already has department/year/section
    public boolean existsExcept(long teacherId, String department, int year, int section, long excludeClassId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String selection = COL_TEACHER_ID + " = ? AND " + COL_DEPARTMENT + " = ? AND "
                + COL_YEAR + " = ? AND " + COL_SECTION + " = ?";
        String[] args;
        if (excludeClassId != -1) {
            selection += " AND " + COL_ID + " != ?";
            args = new String[]{String.valueOf(teacherId), department,
                    String.valueOf(year), String.valueOf(section), String.valueOf(excludeClassId)};
        } else {
            args = new String[]{String.valueOf(teacherId), department,
                    String.valueOf(year), String.valueOf(section)};
        }

        try (Cursor c = db.query(TABLE_CLASSES,
                new String[]{COL_ID},
                selection,
                args,
                null, null, null)) {
            return c.moveToFirst();
        }
    }

    // Saves a new class. Returns the new id, or -1 if it could not be saved.
    public long insert(long teacherId, String department, int year, int section) {
        ContentValues values = new ContentValues();
        values.put(COL_TEACHER_ID, teacherId);
        values.put(COL_DEPARTMENT, department);
        values.put(COL_YEAR, year);
        values.put(COL_SECTION, section);
        return helper.getWritableDatabase().insert(TABLE_CLASSES, null, values);
    }

    // Updates an existing class. Returns true if updated.
    public boolean update(long classId, String department, int year, int section) {
        ContentValues values = new ContentValues();
        values.put(COL_DEPARTMENT, department);
        values.put(COL_YEAR, year);
        values.put(COL_SECTION, section);
        return helper.getWritableDatabase().update(TABLE_CLASSES, values,
                COL_ID + " = ?", new String[]{String.valueOf(classId)}) > 0;
    }

    // Deletes a class. Cascades to students, sessions, and records.
    public boolean delete(long classId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(TABLE_CLASSES, COL_ID + " = ?",
                new String[]{String.valueOf(classId)}) > 0;
    }

    // All classes of this teacher, each with its student count, in stable order
    public List<SchoolClass> listForTeacher(long teacherId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT c." + COL_ID + ", c." + COL_TEACHER_ID + ", c." + COL_DEPARTMENT
                + ", c." + COL_YEAR + ", c." + COL_SECTION
                + ", COUNT(s." + COL_ID + ") AS student_count"
                + " FROM " + TABLE_CLASSES + " c"
                + " LEFT JOIN " + TABLE_STUDENTS + " s ON s." + COL_CLASS_ID + " = c." + COL_ID
                + " WHERE c." + COL_TEACHER_ID + " = ?"
                + " GROUP BY c." + COL_ID
                + " ORDER BY c." + COL_DEPARTMENT + ", c." + COL_YEAR + ", c." + COL_SECTION;

        List<SchoolClass> result = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(teacherId)})) {
            while (c.moveToNext()) {
                result.add(new SchoolClass(
                        c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                        c.getLong(c.getColumnIndexOrThrow(COL_TEACHER_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_DEPARTMENT)),
                        c.getInt(c.getColumnIndexOrThrow(COL_YEAR)),
                        c.getInt(c.getColumnIndexOrThrow(COL_SECTION)),
                        c.getInt(c.getColumnIndexOrThrow("student_count"))));
            }
        }
        return result;
    }
}
