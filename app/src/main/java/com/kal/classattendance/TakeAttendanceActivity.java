package com.kal.classattendance;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.kal.classattendance.data.AttendanceDao;
import com.kal.classattendance.data.StudentDao;
import com.kal.classattendance.model.Student;
import com.kal.classattendance.util.Logout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class TakeAttendanceActivity extends AppCompatActivity {

    // yyyy-MM-dd is how dates are stored in the database
    private static final SimpleDateFormat DB_DATE =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat DISPLAY_DATE =
            new SimpleDateFormat("d MMM yyyy", Locale.US);

    private StudentDao studentDao;
    private AttendanceDao attendanceDao;
    private LayoutInflater inflater;

    private long classId;
    private String classLabel;

    public static final String EXTRA_SESSION_DATE = "session_date";

    // The date this session belongs to; changing it reloads saved marks
    private Calendar sessionDate = Calendar.getInstance();

    // student _id -> "P" / "A" / "L", kept in sync with the radio buttons
    private final Map<Long, String> statuses = new HashMap<>();
    private List<Student> students;

    private LinearLayout listStudents;
    private TextView emptyView;
    private TextView chipPresent;
    private TextView chipAbsent;
    private TextView chipLate;
    private MaterialButton btnDate;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.attendance);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        studentDao = new StudentDao(this);
        attendanceDao = new AttendanceDao(this);
        inflater = LayoutInflater.from(this);

        classId = getIntent().getLongExtra(ClassListActivity.EXTRA_CLASS_ID, -1);
        classLabel = getIntent().getStringExtra(ClassListActivity.EXTRA_CLASS_NAME);
        if (classLabel == null || classLabel.isEmpty()) {
            classLabel = getString(R.string.take_attendance);
        }

        String initialDateStr = getIntent().getStringExtra(EXTRA_SESSION_DATE);
        if (initialDateStr != null && !initialDateStr.isEmpty()) {
            try {
                java.util.Date parsed = DB_DATE.parse(initialDateStr);
                if (parsed != null) {
                    sessionDate.setTime(parsed);
                }
            } catch (Exception ignored) {}
        }

        TextView tvClassName = findViewById(R.id.tvClassName);
        tvClassName.setText(classLabel);

        listStudents = findViewById(R.id.listStudents);
        emptyView = findViewById(R.id.tvEmptyStudents);
        chipPresent = findViewById(R.id.tvChipPresent);
        chipAbsent = findViewById(R.id.tvChipAbsent);
        chipLate = findViewById(R.id.tvChipLate);
        btnDate = findViewById(R.id.btnDate);
        btnSave = findViewById(R.id.btnSaveAttendance);

        // The toolbar declares its own menu in XML (app:menu), so item clicks
        // arrive through this listener, not onCreateOptionsMenu
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_logout) {
                Logout.go(this);
                return true;
            }
            return false;
        });

        // Back arrow returns to the previous screen
        toolbar.setNavigationOnClickListener(v -> finish());

        // Date button opens a date picker; picking a date reloads that day's marks
        btnDate.setOnClickListener(v -> openDatePicker());

        // Everyone present in one tap
        findViewById(R.id.btnMarkAllPresent).setOnClickListener(v -> markAllPresent());

        // Save creates/updates the session for this class + date
        btnSave.setOnClickListener(v -> saveAttendance());

        refreshDateLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload here so students added elsewhere show up, and so a picked
        // date always starts from what is saved in the database
        loadStudents();
    }

    private void openDatePicker() {
        // MaterialDatePicker measures dates in UTC, so build the selection
        // from the session date read through the UTC timezone
        Calendar utcSelection = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcSelection.clear();
        utcSelection.set(sessionDate.get(Calendar.YEAR),
                sessionDate.get(Calendar.MONTH),
                sessionDate.get(Calendar.DAY_OF_MONTH));

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_date)
                .setTheme(R.style.ThemeOverlay_App_DatePicker)
                .setSelection(utcSelection.getTimeInMillis())
                .setCalendarConstraints(buildConstraints())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            // Read the picked date back in UTC to avoid off-by-one days
            Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            picked.setTimeInMillis((Long) selection);
            sessionDate.set(picked.get(Calendar.YEAR),
                    picked.get(Calendar.MONTH),
                    picked.get(Calendar.DAY_OF_MONTH));
            refreshDateLabel();
            loadStudents(); // marks may differ on another day
        });

        picker.show(getSupportFragmentManager(), "date_picker");
    }

    /** Only today and earlier can be picked — attendance cannot be taken for the future. */
    private CalendarConstraints buildConstraints() {
        return new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();
    }

    private void refreshDateLabel() {
        Calendar today = Calendar.getInstance();
        boolean isToday = today.get(Calendar.YEAR) == sessionDate.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == sessionDate.get(Calendar.DAY_OF_YEAR);

        if (isToday) {
            btnDate.setText(getString(R.string.today) + ", " + DISPLAY_DATE.format(sessionDate.getTime()));
        } else {
            btnDate.setText(DISPLAY_DATE.format(sessionDate.getTime()));
        }
    }

    /** Loads the class roster and fills the radio buttons from the database. */
    private void loadStudents() {
        students = studentDao.listForClass(classId);

        listStudents.removeAllViews();
        statuses.clear();

        String dateKey = DB_DATE.format(sessionDate.getTime());
        Map<Long, String> saved = attendanceDao.hasSession(classId, dateKey)
                ? attendanceDao.loadRecords(attendanceDao.getOrCreateSession(classId, dateKey))
                : new HashMap<>();

        for (Student student : students) {
            // Unmarked students default to Present
            String status = saved.containsKey(student.getId())
                    ? saved.get(student.getId())
                    : AttendanceDao.STATUS_PRESENT;
            statuses.put(student.getId(), status);
            listStudents.addView(buildStudentRow(student, status));
        }

        boolean empty = students.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.cardStudents).setVisibility(empty ? View.GONE : View.VISIBLE);
        btnSave.setEnabled(!empty);
        updateChips();
    }

    private View buildStudentRow(Student student, String status) {
        View row = inflater.inflate(R.layout.item_attendance_row, listStudents, false);

        TextView tvName = row.findViewById(R.id.tvStudentName);
        TextView tvId = row.findViewById(R.id.tvStudentId);
        RadioGroup rgStatus = row.findViewById(R.id.rgStatus);
        RadioButton rbPresent = row.findViewById(R.id.rbPresent);
        RadioButton rbAbsent = row.findViewById(R.id.rbAbsent);
        RadioButton rbLate = row.findViewById(R.id.rbLate);

        tvName.setText(student.getFullName());
        tvId.setText(student.getStudentCode());

        // Apply the current status without triggering the change listener yet
        if (AttendanceDao.STATUS_ABSENT.equals(status)) {
            rbAbsent.setChecked(true);
        } else if (AttendanceDao.STATUS_LATE.equals(status)) {
            rbLate.setChecked(true);
        } else {
            rbPresent.setChecked(true);
        }

        // Keep the map and the summary chips in sync as marks change
        rgStatus.setOnCheckedChangeListener((group, checkedId) -> {
            String newStatus;
            if (checkedId == R.id.rbAbsent) {
                newStatus = AttendanceDao.STATUS_ABSENT;
            } else if (checkedId == R.id.rbLate) {
                newStatus = AttendanceDao.STATUS_LATE;
            } else {
                newStatus = AttendanceDao.STATUS_PRESENT;
            }
            statuses.put(student.getId(), newStatus);
            updateChips();
        });

        return row;
    }

    /** Sets every student to Present and refreshes the rows + chips. */
    private void markAllPresent() {
        for (Student student : students) {
            statuses.put(student.getId(), AttendanceDao.STATUS_PRESENT);
        }
        // Simple approach: rebuild the rows so the radios reflect the map
        for (int i = 0; i < students.size(); i++) {
            View row = listStudents.getChildAt(i);
            ((RadioButton) row.findViewById(R.id.rbPresent)).setChecked(true);
        }
        updateChips();
    }

    /** Present / Absent / Late counters under the class name. */
    private void updateChips() {
        int present = 0;
        int absent = 0;
        int late = 0;
        for (String status : statuses.values()) {
            switch (status) {
                case AttendanceDao.STATUS_ABSENT:
                    absent++;
                    break;
                case AttendanceDao.STATUS_LATE:
                    late++;
                    break;
                default:
                    present++;
                    break;
            }
        }
        chipPresent.setText("Present " + present);
        chipAbsent.setText("Absent " + absent);
        chipLate.setText("Late " + late);
    }

    /** Saves the session and every mark, then returns to the previous screen. */
    private void saveAttendance() {
        if (students.isEmpty()) {
            Toast.makeText(this, R.string.no_students_in_class, Toast.LENGTH_SHORT).show();
            return;
        }

        String dateKey = DB_DATE.format(sessionDate.getTime());
        long sessionId = attendanceDao.getOrCreateSession(classId, dateKey);
        boolean saved = attendanceDao.saveRecords(sessionId, statuses);

        if (saved) {
            Toast.makeText(this, "Attendance saved for "
                    + students.size() + (students.size() == 1 ? " student" : " students"),
                    Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Could not save attendance, try again",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
