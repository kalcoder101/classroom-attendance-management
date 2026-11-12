package com.kal.classattendance;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kal.classattendance.data.TeacherDao;
import com.kal.classattendance.model.Teacher;

public class LoginActivity extends AppCompatActivity {

    // Keys used to pass the logged-in teacher to the next screen
    public static final String EXTRA_TEACHER_ID = "teacher_id";
    public static final String EXTRA_TEACHER_NAME = "teacher_name";

    private TeacherDao teacherDao;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(bars.bottom, ime.bottom);
            v.setPadding(bars.left, bars.top, bars.right, bottomPadding);
            return insets;
        });

        teacherDao = new TeacherDao(this);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        ImageView btnTogglePassword = findViewById(R.id.btnTogglePassword);

        // Password visibility toggle
        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_visibility);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        // Register link under the card opens the registration form
        TextView tvRegister = findViewById(R.id.tvRegister);
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        // Login checks the credentials against the teachers table
        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (username.isEmpty()) {
                etUsername.setError("Enter your username");
                etUsername.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                etPassword.setError("Enter your password");
                etPassword.requestFocus();
                return;
            }

            Teacher teacher = teacherDao.login(username, password);
            if (teacher == null) {
                Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
            intent.putExtra(EXTRA_TEACHER_ID, teacher.getId());
            intent.putExtra(EXTRA_TEACHER_NAME, teacher.getFullName());
            startActivity(intent);
            finish(); // back from the dashboard should not return to the login form
        });
    }
}
