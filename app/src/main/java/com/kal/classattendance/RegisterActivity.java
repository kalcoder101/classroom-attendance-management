package com.kal.classattendance;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kal.classattendance.data.TeacherDao;

public class RegisterActivity extends AppCompatActivity {

    private TeacherDao teacherDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        teacherDao = new TeacherDao(this);

        EditText etFullName = findViewById(R.id.etFullName);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etConfirmPassword = findViewById(R.id.etConfirmPassword);

        // "Log in" link goes back to the login screen that opened this form
        findViewById(R.id.tvLoginLink).setOnClickListener(v -> finish());

        // Register validates the form and saves the new teacher
        findViewById(R.id.btnRegister).setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            boolean valid = true;

            if (fullName.isEmpty()) {
                etFullName.setError("Full name is required");
                valid = false;
            }
            if (username.isEmpty()) {
                etUsername.setError("Username is required");
                valid = false;
            } else if (username.contains(" ")) {
                etUsername.setError("Username cannot contain spaces");
                valid = false;
            } else if (teacherDao.usernameExists(username)) {
                etUsername.setError("Username already taken");
                valid = false;
            }
            if (password.length() < 6) {
                etPassword.setError("Password needs at least 6 characters");
                valid = false;
            }
            if (!confirmPassword.equals(password)) {
                etConfirmPassword.setError("Passwords do not match");
                valid = false;
            }
            if (!valid) {
                return;
            }

            long id = teacherDao.register(fullName, username, password);
            if (id == -1) {
                Toast.makeText(this, "Could not save account, try again", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Account created, please log in", Toast.LENGTH_LONG).show();
            finish(); // back to LoginActivity
        });
    }
}
