package com.kal.classattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kal.classattendance.data.AttendanceDao;
import com.kal.classattendance.data.AttendanceDao.AttendanceOverviewStats;
import com.kal.classattendance.data.AttendanceDao.SessionSummary;
import com.kal.classattendance.data.ClassDao;
import com.kal.classattendance.data.StudentDao;
import com.kal.classattendance.data.TeacherDao;
import com.kal.classattendance.model.Teacher;
import com.kal.classattendance.util.Logout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private long teacherId;
    private String teacherName;

    private ClassDao classDao;
    private StudentDao studentDao;
    private AttendanceDao attendanceDao;

    private TextView tvTodayDate;
    private TextView tvGreetingSalutation;
    private TextView tvTeacherName;
    private TextView tvManageClassesCount;
    private TextView tvManageStudentsCount;
    private TextView tvReportsHint;

    private TextView tvLastClass;
    private TextView tvLastRate;
    private TextView tvLastDate;
    private TextView tvLastChipPresent;
    private TextView tvLastChipAbsent;
    private TextView tvLastChipLate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        teacherId = getIntent().getLongExtra(LoginActivity.EXTRA_TEACHER_ID, -1);
        teacherName = getIntent().getStringExtra(LoginActivity.EXTRA_TEACHER_NAME);

        classDao = new ClassDao(this);
        studentDao = new StudentDao(this);
        attendanceDao = new AttendanceDao(this);

        // Fallback: If teacherName was not passed in intent, query from DB
        if (teacherName == null || teacherName.isEmpty()) {
            Teacher teacher = new TeacherDao(this).getById(teacherId);
            if (teacher != null) teacherName = teacher.getFullName();
        }

        tvTodayDate = findViewById(R.id.tvTodayDate);
        tvGreetingSalutation = findViewById(R.id.tvGreetingSalutation);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvManageClassesCount = findViewById(R.id.tvManageClassesCount);
        tvManageStudentsCount = findViewById(R.id.tvManageStudentsCount);
        tvReportsHint = findViewById(R.id.tvReportsHint);

        tvLastClass = findViewById(R.id.tvLastClass);
        tvLastRate = findViewById(R.id.tvLastRate);
        tvLastDate = findViewById(R.id.tvLastDate);
        tvLastChipPresent = findViewById(R.id.tvLastChipPresent);
        tvLastChipAbsent = findViewById(R.id.tvLastChipAbsent);
        tvLastChipLate = findViewById(R.id.tvLastChipLate);

        setupTopGreetings();

        // 1. Manage Classes
        findViewById(R.id.cardManageClasses).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ClassListActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            startActivity(intent);
        });

        // 2. Manage Students
        findViewById(R.id.cardManageStudents).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ManageStudentsActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            startActivity(intent);
        });

        // 3. Take Attendance
        findViewById(R.id.cardTakeAttendance).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ClassListActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            intent.putExtra(ClassListActivity.EXTRA_MODE, ClassListActivity.MODE_ATTENDANCE);
            startActivity(intent);
        });

        // 4. Reports & Analytics
        findViewById(R.id.cardReports).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ReportsActivity.class);
            intent.putExtra(ReportsActivity.EXTRA_TEACHER_ID, teacherId);
            startActivity(intent);
        });

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> Logout.go(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboardData();
    }

    private void setupTopGreetings() {
        // Formatted Today Date: e.g. "TODAY · 12 NOV 2026"
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMM", Locale.US);
        tvTodayDate.setText(dateFmt.format(new Date()).toUpperCase(Locale.US));

        // Time-aware greeting
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String salutation;
        if (hour < 12) {
            salutation = getString(R.string.good_morning) + ",";
        } else if (hour < 17) {
            salutation = getString(R.string.good_afternoon) + ",";
        } else {
            salutation = getString(R.string.good_evening) + ",";
        }

        tvGreetingSalutation.setText(salutation);
        tvTeacherName.setText(teacherName != null && !teacherName.isEmpty() ? teacherName : "Teacher");
    }

    private void refreshDashboardData() {
        // Dynamic Class count
        int classCount = classDao.listForTeacher(teacherId).size();
        tvManageClassesCount.setText(classCount + (classCount == 1 ? " class" : " classes"));

        // Dynamic Student count across all classes
        int studentCount = studentDao.countForTeacher(teacherId);
        tvManageStudentsCount.setText(studentCount + (studentCount == 1 ? " student" : " students"));

        // Dynamic Sessions count for Reports hint
        AttendanceOverviewStats stats = attendanceDao.getOverviewStats(teacherId, -1);
        if (stats.totalSessions > 0) {
            tvReportsHint.setText(stats.totalSessions + (stats.totalSessions == 1 ? " session" : " sessions") + " recorded");
        } else {
            tvReportsHint.setText(R.string.reports_hint);
        }

        updateLastAttendanceCard();
    }

    private void updateLastAttendanceCard() {
        SessionSummary last = attendanceDao.getLatestSessionForTeacher(teacherId);

        if (last != null) {
            tvLastClass.setText(last.getClassLabel());

            // Attendance Rate pill
            tvLastRate.setVisibility(View.VISIBLE);
            tvLastRate.setText(Math.round(last.getAttendanceRate()) + "% attendance");

            // Formatted date
            String formattedDate = last.getSessionDate();
            try {
                SimpleDateFormat dbFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat displayFmt = new SimpleDateFormat("d MMM yyyy", Locale.US);
                Date d = dbFmt.parse(last.getSessionDate());
                if (d != null) formattedDate = displayFmt.format(d);
            } catch (Exception ignored) {}

            tvLastDate.setText(formattedDate);

            // Mini chips
            tvLastChipPresent.setVisibility(View.VISIBLE);
            tvLastChipPresent.setText("P " + last.getPresentCount());

            tvLastChipAbsent.setVisibility(View.VISIBLE);
            tvLastChipAbsent.setText("A " + last.getAbsentCount());

            tvLastChipLate.setVisibility(View.VISIBLE);
            tvLastChipLate.setText("L " + last.getLateCount());

            // Tap to view/edit this session
            findViewById(R.id.cardLastAttendance).setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, TakeAttendanceActivity.class);
                intent.putExtra(ClassListActivity.EXTRA_CLASS_ID, last.getClassId());
                intent.putExtra(ClassListActivity.EXTRA_CLASS_NAME, last.getClassLabel());
                intent.putExtra(TakeAttendanceActivity.EXTRA_SESSION_DATE, last.getSessionDate());
                intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
                startActivity(intent);
            });
        } else {
            tvLastClass.setText(R.string.take_attendance);
            tvLastRate.setVisibility(View.GONE);
            tvLastDate.setText("No sessions taken yet. Tap to start taking attendance.");
            tvLastChipPresent.setVisibility(View.GONE);
            tvLastChipAbsent.setVisibility(View.GONE);
            tvLastChipLate.setVisibility(View.GONE);

            // Tap opens class list in attendance mode
            findViewById(R.id.cardLastAttendance).setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ClassListActivity.class);
                intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
                intent.putExtra(ClassListActivity.EXTRA_MODE, ClassListActivity.MODE_ATTENDANCE);
                startActivity(intent);
            });
        }
    }
}
