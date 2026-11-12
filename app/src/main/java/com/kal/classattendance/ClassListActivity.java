package com.kal.classattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kal.classattendance.data.ClassDao;
import com.kal.classattendance.model.SchoolClass;
import com.kal.classattendance.util.Logout;

import java.util.List;

public class ClassListActivity extends AppCompatActivity {

    // Keys used to pass the selected class to the next screen
    public static final String EXTRA_CLASS_ID = "class_id";
    public static final String EXTRA_CLASS_NAME = "class_name";

    // When set to "attendance", tapping a class opens Take Attendance
    // instead of the student list (Dashboard's Take Attendance tile)
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_ATTENDANCE = "attendance";

    private ClassDao classDao;
    private long teacherId;
    private boolean attendanceMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.classes);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        teacherId = getIntent().getLongExtra(LoginActivity.EXTRA_TEACHER_ID, -1);
        attendanceMode = MODE_ATTENDANCE.equals(
                getIntent().getStringExtra(EXTRA_MODE));
        classDao = new ClassDao(this);

        // Back arrow returns to the dashboard
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnAddClass).setOnClickListener(v -> {
            Intent intent = new Intent(ClassListActivity.this, AddClassActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            startActivity(intent);
        });

        // Logout icon returns to the login screen and clears the back stack
        findViewById(R.id.btnLogout).setOnClickListener(v -> Logout.go(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reloaded here (not onCreate) so the list refreshes after AddClassActivity saves
        loadClasses();
    }

    private void loadClasses() {
        LinearLayout container = findViewById(R.id.listClasses);
        TextView emptyView = findViewById(R.id.tvEmptyClasses);
        LayoutInflater inflater = LayoutInflater.from(this);

        container.removeAllViews();
        List<SchoolClass> classes = classDao.listForTeacher(teacherId);
        emptyView.setVisibility(classes.isEmpty() ? View.VISIBLE : View.GONE);

        for (SchoolClass schoolClass : classes) {
            View card = inflater.inflate(R.layout.item_class_card, container, false);

            TextView tvName = card.findViewById(R.id.tvClassName);
            TextView tvCount = card.findViewById(R.id.tvClassCount);
            tvName.setText(schoolClass.getLabel());
            tvCount.setText(schoolClass.getStudentCount() + " students");

            card.setOnClickListener(v -> {
                // In attendance mode a class tap starts taking attendance;
                // otherwise it opens that class's student list
                Intent intent;
                if (attendanceMode) {
                    intent = new Intent(ClassListActivity.this, TakeAttendanceActivity.class);
                } else {
                    intent = new Intent(ClassListActivity.this, ManageStudentsActivity.class);
                }
                intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
                intent.putExtra(EXTRA_CLASS_ID, schoolClass.getId());
                intent.putExtra(EXTRA_CLASS_NAME, schoolClass.getLabel());
                startActivity(intent);
            });

            container.addView(card);
        }
    }
}
