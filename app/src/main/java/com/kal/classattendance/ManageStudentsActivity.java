package com.kal.classattendance;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import com.kal.classattendance.data.StudentDao;
import com.kal.classattendance.model.SchoolClass;
import com.kal.classattendance.model.Student;

import java.util.ArrayList;
import java.util.List;

public class ManageStudentsActivity extends AppCompatActivity {

    private StudentDao studentDao;
    private long teacherId;

    // All of this teacher's classes; index 0 of the spinner is the "All classes" filter
    private List<SchoolClass> classes = new ArrayList<>();
    private List<Student> allStudents = new ArrayList<>();

    private Spinner spClassFilter;
    private EditText etSearch;
    private TextView tvShowing;
    private LinearLayout listStudents;
    private TextView emptyView;
    private LayoutInflater inflater;
    private Student selectedStudentForContextMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.students);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        teacherId = getIntent().getLongExtra(LoginActivity.EXTRA_TEACHER_ID, -1);
        long openedClassId = getIntent().getLongExtra(ClassListActivity.EXTRA_CLASS_ID, -1);
        studentDao = new StudentDao(this);
        inflater = LayoutInflater.from(this);

        spClassFilter = findViewById(R.id.spClassFilter);
        etSearch = findViewById(R.id.etSearch);
        tvShowing = findViewById(R.id.tvShowing);
        listStudents = findViewById(R.id.listStudents);
        emptyView = findViewById(R.id.tvEmptyStudents);

        // Class filter: "All classes" + one entry per real class
        List<String> filterLabels = new ArrayList<>();
        filterLabels.add(getString(R.string.filter_all_classes));
        for (SchoolClass c : new ClassDao(this).listForTeacher(teacherId)) {
            classes.add(c);
            filterLabels.add(c.getLabel());
        }
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, filterLabels);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spClassFilter.setAdapter(filterAdapter);

        // Preselect the class this screen was opened from, if any.
        // setSelection() is dropped if called before the spinner's first layout
        // pass, so also post it — this makes the selection stick reliably.
        if (openedClassId != -1) {
            for (int i = 0; i < classes.size(); i++) {
                if (classes.get(i).getId() == openedClassId) {
                    final int index = i + 1; // +1 skips the "All classes" entry
                    spClassFilter.setSelection(index, false);
                    spClassFilter.post(() -> spClassFilter.setSelection(index, false));
                    break;
                }
            }
        }

        spClassFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                renderStudents();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // Live search over name and student code
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                renderStudents();
            }
        });

        // Add Student opens the form, carrying the currently selected class
        findViewById(R.id.btnAddStudent).setOnClickListener(v -> {
            Intent intent = new Intent(ManageStudentsActivity.this, AddStudentActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            intent.putExtra(ClassListActivity.EXTRA_CLASS_ID, selectedClassId());
            startActivity(intent);
        });

        // Back arrow returns to the previous screen
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload here so students added in AddStudentActivity appear immediately
        allStudents = studentDao.listForTeacher(teacherId);
        renderStudents();
    }

    /** _id of the class picked in the filter spinner, or -1 for "All classes" */
    private long selectedClassId() {
        int position = spClassFilter.getSelectedItemPosition();
        if (position <= 0 || position > classes.size()) {
            return -1;
        }
        return classes.get(position - 1).getId();
    }

    private void renderStudents() {
        String query = etSearch.getText().toString().trim().toLowerCase();
        long classId = selectedClassId();

        listStudents.removeAllViews();
        int shown = 0;

        for (Student student : allStudents) {
            if (classId != -1 && student.getClassId() != classId) {
                continue;
            }
            boolean matches = query.isEmpty()
                    || student.getFullName().toLowerCase().contains(query)
                    || student.getStudentCode().toLowerCase().contains(query);
            if (!matches) {
                continue;
            }
            listStudents.addView(buildStudentRow(student));
            shown++;
        }

        String label = "Showing " + shown
                + (shown == 1 ? " student" : " students");
        tvShowing.setText(label);
        emptyView.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private View buildStudentRow(Student student) {
        View row = inflater.inflate(R.layout.item_student_row, listStudents, false);

        TextView tvName = row.findViewById(R.id.tvStudentName);
        TextView tvInfo = row.findViewById(R.id.tvStudentInfo);
        tvName.setText(student.getFullName());
        tvInfo.setText(student.getInfoLine());

        // Attach student data to row tag for Context Menu lookup
        row.setTag(student);
        registerForContextMenu(row);

        // Edit dialog
        row.findViewById(R.id.btnEdit).setOnClickListener(v -> showEditDialog(student));

        // Delete asks first, then removes from the database
        row.findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDeleteStudent(student));

        return row;
    }

    private void confirmDeleteStudent(Student student) {
        new AlertDialog.Builder(this)
                .setTitle("Delete student")
                .setMessage("Remove " + student.getFullName() + " ("
                        + student.getStudentCode() + ")?")
                .setPositiveButton("Delete", (d, w) -> {
                    studentDao.delete(student.getId());
                    allStudents = studentDao.listForTeacher(teacherId);
                    renderStudents();
                    Toast.makeText(this, "Student deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getTag() instanceof Student) {
            selectedStudentForContextMenu = (Student) v.getTag();
            menu.setHeaderTitle(selectedStudentForContextMenu.getFullName() + " (" + selectedStudentForContextMenu.getStudentCode() + ")");
            getMenuInflater().inflate(R.menu.menu_student_context, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedStudentForContextMenu == null) {
            return super.onContextItemSelected(item);
        }

        int id = item.getItemId();
        if (id == R.id.ctx_edit_student) {
            showEditDialog(selectedStudentForContextMenu);
            return true;
        } else if (id == R.id.ctx_view_attendance) {
            Intent intent = new Intent(this, TakeAttendanceActivity.class);
            intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
            intent.putExtra(ClassListActivity.EXTRA_CLASS_ID, selectedStudentForContextMenu.getClassId());
            startActivity(intent);
            return true;
        } else if (id == R.id.ctx_delete_student) {
            confirmDeleteStudent(selectedStudentForContextMenu);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    private void showEditDialog(Student student) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_student, null);
        EditText etName = dialogView.findViewById(R.id.etEditStudentName);
        EditText etCode = dialogView.findViewById(R.id.etEditStudentCode);
        Spinner spClass = dialogView.findViewById(R.id.spEditStudentClass);

        etName.setText(student.getFullName());
        etCode.setText(student.getStudentCode());

        // Fill class spinner with teacher's classes
        List<String> classNames = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < classes.size(); i++) {
            SchoolClass c = classes.get(i);
            classNames.add(c.getLabel());
            if (c.getId() == student.getClassId()) {
                selectedIndex = i;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                classNames
        );
        spClass.setAdapter(adapter);
        spClass.setSelection(selectedIndex);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancelEditStudent).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnSaveEditStudent).setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String newCode = etCode.getText().toString().trim();

            if (newName.isEmpty()) {
                etName.setError("Full name is required");
                return;
            }
            if (newCode.isEmpty()) {
                etCode.setError("Student ID is required");
                return;
            }

            int pos = spClass.getSelectedItemPosition();
            if (pos < 0 || pos >= classes.size()) {
                Toast.makeText(this, "Please select a valid class", Toast.LENGTH_SHORT).show();
                return;
            }
            long newClassId = classes.get(pos).getId();

            if (studentDao.existsExcept(newClassId, newCode, student.getId())) {
                etCode.setError("Another student in this class already has this ID");
                return;
            }

            boolean updated = studentDao.update(student.getId(), newClassId, newCode, newName);
            if (updated) {
                Toast.makeText(this, "Student updated successfully", Toast.LENGTH_SHORT).show();
                allStudents = studentDao.listForTeacher(teacherId);
                renderStudents();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Failed to update student", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
