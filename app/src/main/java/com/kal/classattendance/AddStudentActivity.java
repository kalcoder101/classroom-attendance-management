package com.kal.classattendance;

import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.kal.classattendance.data.ClassDao;
import com.kal.classattendance.data.StudentDao;
import com.kal.classattendance.model.SchoolClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddStudentActivity extends AppCompatActivity {

    private StudentDao studentDao;
    private long teacherId;
    private List<SchoolClass> classes = new ArrayList<>();

    private Spinner spClass;
    private EditText etStudentId;
    private EditText etStudentName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.add_student);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        teacherId = getIntent().getLongExtra(LoginActivity.EXTRA_TEACHER_ID, -1);
        long preselectedClassId = getIntent().getLongExtra(ClassListActivity.EXTRA_CLASS_ID, -1);
        studentDao = new StudentDao(this);

        spClass = findViewById(R.id.spClass);
        etStudentId = findViewById(R.id.etStudentId);
        etStudentName = findViewById(R.id.etStudentName);
        MaterialButton btnSave = findViewById(R.id.btnSaveAddAnother);
        MaterialButton btnDone = findViewById(R.id.btnDone);

        // Class picker shows the teacher's real classes
        List<String> labels = new ArrayList<>();
        for (SchoolClass c : new ClassDao(this).listForTeacher(teacherId)) {
            classes.add(c);
            labels.add(c.getLabel());
        }
        ArrayAdapter<String> classAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spClass.setAdapter(classAdapter);

        // Preselect the class this screen was opened from
        if (preselectedClassId != -1) {
            for (int i = 0; i < classes.size(); i++) {
                if (classes.get(i).getId() == preselectedClassId) {
                    spClass.setSelection(i);
                    break;
                }
            }
        }

        // "All classes" would leave the target class ambiguous, so guard against it
        if (classes.isEmpty()) {
            Toast.makeText(this, R.string.create_class_first, Toast.LENGTH_LONG).show();
            btnSave.setEnabled(false);
            btnDone.setEnabled(false);
        }

        btnSave.setOnClickListener(v -> saveStudent(true));
        btnDone.setOnClickListener(v -> saveStudent(false));

        // Back arrow returns to the previous screen
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void saveStudent(boolean addAnother) {
        int position = spClass.getSelectedItemPosition();
        if (position == AdapterView.INVALID_POSITION || position >= classes.size()) {
            Toast.makeText(this, R.string.create_class_first, Toast.LENGTH_SHORT).show();
            return;
        }
        SchoolClass selectedClass = classes.get(position);

        // Locale.ROOT keeps ID normalisation identical on every device
        String studentCode = etStudentId.getText().toString().trim().toUpperCase(Locale.ROOT);
        String fullName = etStudentName.getText().toString().trim();

        boolean valid = true;
        if (studentCode.isEmpty()) {
            etStudentId.setError(getString(R.string.error_student_id_required));
            valid = false;
        } else if (studentDao.exists(selectedClass.getId(), studentCode)) {
            etStudentId.setError(getString(R.string.error_student_code_exists, selectedClass.getLabel()));
            valid = false;
        }
        if (fullName.isEmpty()) {
            etStudentName.setError(getString(R.string.error_student_name_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        long id = studentDao.insert(selectedClass.getId(), studentCode, fullName);
        if (id == -1) {
            Toast.makeText(this, R.string.student_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (addAnother) {
            Toast.makeText(this,
                    getString(R.string.student_added_to, fullName, selectedClass.getLabel()),
                    Toast.LENGTH_SHORT).show();
            etStudentId.setText("");
            etStudentName.setText("");
            etStudentId.requestFocus();
        } else {
            Toast.makeText(this, getString(R.string.student_added, fullName), Toast.LENGTH_SHORT).show();
            finish(); // back to Manage Students, which reloads in onResume
        }
    }
}
