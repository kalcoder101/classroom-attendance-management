package com.kal.classattendance;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import com.kal.classattendance.LoginActivity;

public class AddClassActivity extends AppCompatActivity {

    private Spinner spYear;
    private Spinner spSection;
    private EditText etDepartment;
    private MaterialButton btnSaveClass;
    private Button btnCancel;
    private ClassDao classDao;
    private long teacherId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.add_class);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etDepartment = findViewById(R.id.etDepartment);
        spYear = findViewById(R.id.spYear);
        spSection = findViewById(R.id.spSection);
        btnSaveClass = findViewById(R.id.btnSaveClass);
        btnCancel = findViewById(R.id.btnCancel);

        teacherId = getIntent().getLongExtra(LoginActivity.EXTRA_TEACHER_ID, -1);
        classDao = new ClassDao(this);

        setupSpinner(spYear, R.array.year_options);
        setupSpinner(spSection, R.array.section_options);

        btnSaveClass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String department = etDepartment.getText().toString().trim();
                if (department.isEmpty()) {
                    etDepartment.setError(getString(R.string.hint_department));
                    etDepartment.requestFocus();
                    return;
                }
                // Spinner positions map directly to the numeric values (index 0 = 1st Year / Section 1)
                int year = spYear.getSelectedItemPosition() + 1;
                int section = spSection.getSelectedItemPosition() + 1;

                if (classDao.exists(teacherId, department, year, section)) {
                    etDepartment.setError("You already have this class");
                    etDepartment.requestFocus();
                    return;
                }

                long id = classDao.insert(teacherId, department, year, section);
                if (id == -1) {
                    Toast.makeText(AddClassActivity.this,
                            "Could not save class, try again", Toast.LENGTH_SHORT).show();
                    return;
                }

                String classSummary = department + " · " + spYear.getSelectedItem() + " · " + spSection.getSelectedItem();
                Toast.makeText(AddClassActivity.this, classSummary + " created", Toast.LENGTH_SHORT).show();
                finish(); // back to ClassListActivity, which reloads the list in onResume
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Back arrow returns to the previous screen
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupSpinner(Spinner spinner, int optionsResId) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, optionsResId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

}
