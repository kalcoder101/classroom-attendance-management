package com.kal.classattendance.data;

import static com.kal.classattendance.data.DatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.kal.classattendance.model.Teacher;
import com.kal.classattendance.util.PasswordUtil;

public class TeacherDao {

    private final DatabaseHelper helper;

    public TeacherDao(Context context) {
        helper = DatabaseHelper.getInstance(context);
    }

    // true if a teacher with this username already exists
    public boolean usernameExists(String username) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_TEACHERS,
                new String[]{COL_ID},
                COL_USERNAME + " = ?",
                new String[]{username},
                null, null, null)) {
            return c.moveToFirst();
        }
    }

    // Saves a new teacher. Returns the new id, or -1 if it could not be saved.
    public long register(String fullName, String username, String password) {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(password, salt);

        ContentValues values = new ContentValues();
        values.put(COL_FULL_NAME, fullName);
        values.put(COL_USERNAME, username);
        values.put(COL_PASSWORD_HASH, hash);
        values.put(COL_SALT, salt);

        return helper.getWritableDatabase().insert(TABLE_TEACHERS, null, values);
    }

    // Returns the teacher if username and password are right, otherwise null
    public Teacher login(String username, String password) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_TEACHERS,
                new String[]{COL_ID, COL_FULL_NAME, COL_PASSWORD_HASH, COL_SALT},
                COL_USERNAME + " = ?",
                new String[]{username},
                null, null, null)) {

            if (!c.moveToFirst()) {
                return null;                                   // no such username
            }
            String salt = c.getString(c.getColumnIndexOrThrow(COL_SALT));
            String storedHash = c.getString(c.getColumnIndexOrThrow(COL_PASSWORD_HASH));

            if (!PasswordUtil.matches(password, salt, storedHash)) {
                return null;                                   // wrong password
            }
            return new Teacher(
                    c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_FULL_NAME)));
        }
    }

    // Finds a teacher by id (used later to show the name after login)
    public Teacher getById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_TEACHERS,
                new String[]{COL_ID, COL_FULL_NAME},
                COL_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null)) {

            if (!c.moveToFirst()) {
                return null;
            }
            return new Teacher(
                    c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_FULL_NAME)));
        }
    }
}