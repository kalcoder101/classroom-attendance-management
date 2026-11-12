package com.kal.classattendance.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "class_attendance.db";
    public static final int DB_VERSION = 1;

    // ---------- Table names ----------
    public static final String TABLE_TEACHERS = "teachers";
    public static final String TABLE_CLASSES = "classes";
    public static final String TABLE_STUDENTS = "students";
    public static final String TABLE_SESSIONS = "attendance_sessions";
    public static final String TABLE_RECORDS = "attendance_records";

    // ---------- Column names ----------
    public static final String COL_ID = "_id";            // must be called _id (SimpleCursorAdapter needs it)
    public static final String COL_FULL_NAME = "full_name";
    public static final String COL_USERNAME = "username";
    public static final String COL_PASSWORD_HASH = "password_hash";
    public static final String COL_SALT = "salt";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_TEACHER_ID = "teacher_id";
    public static final String COL_DEPARTMENT = "department";
    public static final String COL_YEAR = "year";
    public static final String COL_SECTION = "section";
    public static final String COL_CLASS_ID = "class_id";
    public static final String COL_STUDENT_CODE = "student_code";
    public static final String COL_SESSION_DATE = "session_date";
    public static final String COL_SESSION_ID = "session_id";
    public static final String COL_STUDENT_ID = "student_id";
    public static final String COL_STATUS = "status";

    // ---------- CREATE TABLE statements ----------
    private static final String CREATE_TEACHERS =
            "CREATE TABLE " + TABLE_TEACHERS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_FULL_NAME + " TEXT NOT NULL, " +
                    COL_USERNAME + " TEXT NOT NULL UNIQUE COLLATE NOCASE, " +
                    COL_PASSWORD_HASH + " TEXT NOT NULL, " +
                    COL_SALT + " TEXT NOT NULL, " +
                    COL_CREATED_AT + " TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)";

    private static final String CREATE_CLASSES =
            "CREATE TABLE " + TABLE_CLASSES + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_TEACHER_ID + references(TABLE_TEACHERS) +
                    COL_DEPARTMENT + " TEXT NOT NULL COLLATE NOCASE, " +
                    COL_YEAR + " INTEGER NOT NULL CHECK (" + COL_YEAR + " BETWEEN 1 AND 5), " +
                    COL_SECTION + " INTEGER NOT NULL CHECK (" + COL_SECTION + " >= 1), " +
                    "UNIQUE (" + COL_TEACHER_ID + ", " + COL_DEPARTMENT + ", "
                    + COL_YEAR + ", " + COL_SECTION + "))";

    private static final String CREATE_STUDENTS =
            "CREATE TABLE " + TABLE_STUDENTS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_CLASS_ID + references(TABLE_CLASSES) +
                    COL_STUDENT_CODE + " TEXT NOT NULL COLLATE NOCASE, " +
                    COL_FULL_NAME + " TEXT NOT NULL, " +
                    "UNIQUE (" + COL_CLASS_ID + ", " + COL_STUDENT_CODE + "))";

    private static final String CREATE_SESSIONS =
            "CREATE TABLE " + TABLE_SESSIONS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_CLASS_ID + references(TABLE_CLASSES) +
                    COL_SESSION_DATE + " TEXT NOT NULL, " +           // stored as yyyy-MM-dd
                    "UNIQUE (" + COL_CLASS_ID + ", " + COL_SESSION_DATE + "))";

    private static final String CREATE_RECORDS =
            "CREATE TABLE " + TABLE_RECORDS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_SESSION_ID + references(TABLE_SESSIONS) +
                    COL_STUDENT_ID + references(TABLE_STUDENTS) +
                    COL_STATUS + " TEXT NOT NULL CHECK (" + COL_STATUS + " IN ('P', 'A', 'L')), " +
                    "UNIQUE (" + COL_SESSION_ID + ", " + COL_STUDENT_ID + "))";

    // " INTEGER NOT NULL REFERENCES table(_id) ON DELETE CASCADE, "
    private static String references(String table) {
        return " INTEGER NOT NULL REFERENCES " + table + "(" + COL_ID
                + ") ON DELETE CASCADE, ";
    }

    // ---------- One shared instance for the whole app ----------
    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ---------- Lifecycle callbacks ----------
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);   // SQLite ignores foreign keys unless this is on
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TEACHERS);
        db.execSQL(CREATE_CLASSES);
        db.execSQL(CREATE_STUDENTS);
        db.execSQL(CREATE_SESSIONS);
        db.execSQL(CREATE_RECORDS);
        db.execSQL("CREATE INDEX idx_records_student ON " + TABLE_RECORDS
                + "(" + COL_STUDENT_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Development only: rebuild everything. Children are dropped before parents.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STUDENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CLASSES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TEACHERS);
        onCreate(db);
    }
}