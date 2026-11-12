package com.kal.classattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
        // Reloaded here so the list refreshes after edits or additions
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
            ImageView btnEdit = card.findViewById(R.id.btnEditClass);
            ImageView btnDelete = card.findViewById(R.id.btnDeleteClass);

            tvName.setText(schoolClass.getLabel());
            tvCount.setText(schoolClass.getStudentCount() + (schoolClass.getStudentCount() == 1 ? " student" : " students"));

            // In attendance mode, hide edit/delete buttons to keep UI focused on picking a class
            if (attendanceMode) {
                btnEdit.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
            } else {
                btnEdit.setVisibility(View.VISIBLE);
                btnDelete.setVisibility(View.VISIBLE);

                btnEdit.setOnClickListener(v -> showEditClassDialog(schoolClass));
                btnDelete.setOnClickListener(v -> showDeleteClassConfirmation(schoolClass));
            }

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

    private void showEditClassDialog(SchoolClass schoolClass) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_class, null);
        EditText etDepartment = dialogView.findViewById(R.id.etEditDepartment);
        Spinner spYear = dialogView.findViewById(R.id.spEditYear);
        Spinner spSection = dialogView.findViewById(R.id.spEditSection);

        etDepartment.setText(schoolClass.getDepartment());
        spYear.setSelection(Math.max(0, schoolClass.getYear() - 1));
        spSection.setSelection(Math.max(0, schoolClass.getSection() - 1));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancelEditClass).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnSaveEditClass).setOnClickListener(v -> {
            String department = etDepartment.getText().toString().trim().toUpperCase();
            if (department.isEmpty()) {
                etDepartment.setError("Department is required");
                return;
            }

            int year = spYear.getSelectedItemPosition() + 1;
            int section = spSection.getSelectedItemPosition() + 1;

            if (classDao.existsExcept(teacherId, department, year, section, schoolClass.getId())) {
                etDepartment.setError("You already have this class");
                return;
            }

            boolean updated = classDao.update(schoolClass.getId(), department, year, section);
            if (updated) {
                Toast.makeText(this, "Class updated successfully", Toast.LENGTH_SHORT).show();
                loadClasses();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Failed to update class", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showDeleteClassConfirmation(SchoolClass schoolClass) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Class")
                .setMessage("Are you sure you want to delete \"" + schoolClass.getLabel() + "\"?\n\n"
                        + "This will permanently remove all enrolled students and all attendance history recorded for this class.")
                .setPositiveButton("Delete Class", (d, which) -> {
                    boolean deleted = classDao.delete(schoolClass.getId());
                    if (deleted) {
                        Toast.makeText(this, "Class deleted", Toast.LENGTH_SHORT).show();
                        loadClasses();
                    } else {
                        Toast.makeText(this, "Could not delete class", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
