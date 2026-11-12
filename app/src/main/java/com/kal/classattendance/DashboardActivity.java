package com.kal.classattendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kal.classattendance.data.ClassDao;
import com.kal.classattendance.util.Logout;

public class DashboardActivity extends AppCompatActivity {

    private long teacherId;

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
        String teacherName = getIntent().getStringExtra(LoginActivity.EXTRA_TEACHER_NAME);

        // Personalise the greeting
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        if (teacherName != null && !teacherName.isEmpty()) {
            tvWelcome.setText(getString(R.string.welcome_back) + ", " + teacherName);
        }

        // Show the real number of classes instead of the placeholder
        ClassDao classDao = new ClassDao(this);
        TextView tvClassCount = findViewById(R.id.tvManageClassesCount);
        int classCount = classDao.listForTeacher(teacherId).size();
        tvClassCount.setText(classCount + (classCount == 1 ? " class" : " classes"));

        // Manage Classes opens the class list for this teacher
        findViewById(R.id.cardManageClasses).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ClassListActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            startActivity(intent);
        });

        // Manage Students opens the student list across all classes
        findViewById(R.id.cardManageStudents).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ManageStudentsActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            startActivity(intent);
        });

        // Take Attendance opens the class picker in attendance mode
        findViewById(R.id.cardTakeAttendance).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ClassListActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            intent.putExtra(ClassListActivity.EXTRA_MODE, ClassListActivity.MODE_ATTENDANCE);
            startActivity(intent);
        });

        // Logout icon returns to the login screen and clears the back stack
        findViewById(R.id.btnLogout).setOnClickListener(v -> Logout.go(this));
    }
}
