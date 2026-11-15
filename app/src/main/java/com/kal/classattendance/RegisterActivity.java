package com.kal.classattendance;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kal.classattendance.data.TeacherDao;
import com.kal.classattendance.util.AppExecutors;

public class RegisterActivity extends AppCompatActivity {

    private TeacherDao teacherDao;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(bars.bottom, ime.bottom);
            v.setPadding(bars.left, bars.top, bars.right, bottomPadding);
            return insets;
        });

        teacherDao = new TeacherDao(this);

        EditText etFullName = findViewById(R.id.etFullName);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etConfirmPassword = findViewById(R.id.etConfirmPassword);
        ImageView btnTogglePassword = findViewById(R.id.btnTogglePassword);
        ImageView btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);

        // Password visibility toggles
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

        btnToggleConfirmPassword.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            if (isConfirmPasswordVisible) {
                etConfirmPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnToggleConfirmPassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                etConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnToggleConfirmPassword.setImageResource(R.drawable.ic_visibility);
            }
            etConfirmPassword.setSelection(etConfirmPassword.getText().length());
        });

        // "Log in" link goes back to the login screen that opened this form
        findViewById(R.id.tvLoginLink).setOnClickListener(v -> finish());

        // Register validates the form and saves the new teacher
        View btnRegister = findViewById(R.id.btnRegister);
        btnRegister.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            boolean valid = true;

            if (fullName.isEmpty()) {
                etFullName.setError(getString(R.string.error_full_name_required));
                valid = false;
            }
            if (username.isEmpty()) {
                etUsername.setError(getString(R.string.error_username_required));
                valid = false;
            } else if (username.contains(" ")) {
                etUsername.setError(getString(R.string.error_username_no_spaces));
                valid = false;
            }
            if (password.length() < 6) {
                etPassword.setError(getString(R.string.error_password_length));
                valid = false;
            }
            if (!confirmPassword.equals(password)) {
                etConfirmPassword.setError(getString(R.string.error_passwords_mismatch));
                valid = false;
            }
            if (!valid) {
                return;
            }

            // Salting and PBKDF2 hashing run off the UI thread; the username
            // check is repeated here, right before the insert, so the check
            // and the write happen on the same worker (the UNIQUE constraint
            // on the column is the final safety net)
            btnRegister.setEnabled(false);
            AppExecutors.runOnDb(() -> {
                if (teacherDao.usernameExists(username)) {
                    AppExecutors.runOnMain(() -> {
                        btnRegister.setEnabled(true);
                        etUsername.setError(getString(R.string.error_username_taken));
                    });
                    return;
                }
                long id = teacherDao.register(fullName, username, password);
                AppExecutors.runOnMain(() -> {
                    btnRegister.setEnabled(true);
                    if (id == -1) {
                        Toast.makeText(this, R.string.register_failed,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.account_created, Toast.LENGTH_LONG).show();
                    finish(); // back to LoginActivity
                });
            });
        });
    }
}
