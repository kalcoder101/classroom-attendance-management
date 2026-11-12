package com.kal.classattendance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.kal.classattendance.data.AttendanceDao;
import com.kal.classattendance.data.AttendanceDao.AttendanceOverviewStats;
import com.kal.classattendance.data.AttendanceDao.SessionSummary;
import com.kal.classattendance.data.ClassDao;
import com.kal.classattendance.model.SchoolClass;
import com.kal.classattendance.util.PdfReportExporter;
import com.kal.classattendance.view.AttendanceChartView;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    public static final String EXTRA_TEACHER_ID = "teacher_id";
    public static final String EXTRA_CLASS_ID = "class_id";

    private static final SimpleDateFormat DB_DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat DISPLAY_DATE = new SimpleDateFormat("d MMM yyyy", Locale.US);

    private AttendanceDao attendanceDao;
    private ClassDao classDao;
    private long teacherId;
    private long selectedClassId = -1; // -1 means "All Classes"

    private Spinner spClass;
    private TextView tvOverallRate;
    private TextView tvTotalSessionsCount;
    private TextView tvKpiPresent;
    private TextView tvKpiAbsent;
    private TextView tvKpiLate;
    private LinearLayout cardChart;
    private AttendanceChartView chartAttendance;
    private LinearLayout cardSessions;
    private LinearLayout listSessions;
    private TextView tvEmptySessions;
    private MaterialButton btnExportPdf;

    private List<SchoolClass> teacherClasses = new ArrayList<>();
    private List<SessionSummary> currentSessions = new ArrayList<>();
    private AttendanceOverviewStats currentStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.reports);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        teacherId = getIntent().getLongExtra(EXTRA_TEACHER_ID, -1);
        selectedClassId = getIntent().getLongExtra(EXTRA_CLASS_ID, -1);

        attendanceDao = new AttendanceDao(this);
        classDao = new ClassDao(this);

        // Bind UI Views
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        View btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> com.kal.classattendance.util.Logout.go(this));
        }

        spClass = findViewById(R.id.spClass);
        tvOverallRate = findViewById(R.id.tvOverallRate);
        tvTotalSessionsCount = findViewById(R.id.tvTotalSessionsCount);
        tvKpiPresent = findViewById(R.id.tvKpiPresent);
        tvKpiAbsent = findViewById(R.id.tvKpiAbsent);
        tvKpiLate = findViewById(R.id.tvKpiLate);
        cardChart = findViewById(R.id.cardChart);
        chartAttendance = findViewById(R.id.chartAttendance);
        cardSessions = findViewById(R.id.cardSessions);
        listSessions = findViewById(R.id.listSessions);
        tvEmptySessions = findViewById(R.id.tvEmptySessions);
        btnExportPdf = findViewById(R.id.btnExportPdf);

        btnExportPdf.setOnClickListener(v -> exportPdf());

        setupClassFilter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload report data in case attendance was taken/updated
        loadReportData();
    }

    private void setupClassFilter() {
        teacherClasses = classDao.listForTeacher(teacherId);

        List<String> options = new ArrayList<>();
        options.add(getString(R.string.all_classes));

        int selectedIndex = 0;
        for (int i = 0; i < teacherClasses.size(); i++) {
            SchoolClass c = teacherClasses.get(i);
            options.add(c.getLabel());
            if (c.getId() == selectedClassId) {
                selectedIndex = i + 1;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                options
        );
        spClass.setAdapter(adapter);

        if (selectedIndex > 0 && selectedIndex < options.size()) {
            spClass.setSelection(selectedIndex);
        }

        spClass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedClassId = -1;
                } else {
                    selectedClassId = teacherClasses.get(position - 1).getId();
                }
                loadReportData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadReportData() {
        currentStats = attendanceDao.getOverviewStats(teacherId, selectedClassId);
        currentSessions = attendanceDao.getSessionSummaries(teacherId, selectedClassId);

        // Update KPI metrics
        tvOverallRate.setText(Math.round(currentStats.attendanceRate) + "%");
        tvTotalSessionsCount.setText(String.valueOf(currentStats.totalSessions));
        tvKpiPresent.setText("Present " + currentStats.totalPresent);
        tvKpiAbsent.setText("Absent " + currentStats.totalAbsent);
        tvKpiLate.setText("Late " + currentStats.totalLate);

        // Update Chart
        chartAttendance.setSessions(currentSessions);

        // Populate session list
        listSessions.removeAllViews();
        boolean hasSessions = !currentSessions.isEmpty();

        cardSessions.setVisibility(hasSessions ? View.VISIBLE : View.GONE);
        cardChart.setVisibility(hasSessions ? View.VISIBLE : View.GONE);
        tvEmptySessions.setVisibility(hasSessions ? View.GONE : View.VISIBLE);
        btnExportPdf.setEnabled(true);

        if (hasSessions) {
            LayoutInflater inflater = LayoutInflater.from(this);
            for (SessionSummary session : currentSessions) {
                View row = inflater.inflate(R.layout.item_report_session_card, listSessions, false);

                TextView tvDate = row.findViewById(R.id.tvSessionDate);
                TextView tvClass = row.findViewById(R.id.tvSessionClass);
                TextView tvRate = row.findViewById(R.id.tvAttendanceRate);
                TextView tvPresent = row.findViewById(R.id.tvCountPresent);
                TextView tvAbsent = row.findViewById(R.id.tvCountAbsent);
                TextView tvLate = row.findViewById(R.id.tvCountLate);

                // Format display date
                String formattedDate = session.getSessionDate();
                try {
                    Date d = DB_DATE.parse(session.getSessionDate());
                    if (d != null) formattedDate = DISPLAY_DATE.format(d);
                } catch (ParseException ignored) {}

                tvDate.setText(formattedDate);
                tvClass.setText(session.getClassLabel());
                tvRate.setText(Math.round(session.getAttendanceRate()) + "%");

                tvPresent.setText("P " + session.getPresentCount());
                tvAbsent.setText("A " + session.getAbsentCount());
                tvLate.setText("L " + session.getLateCount());

                // Tapping a session row opens TakeAttendanceActivity with that class & date
                row.setOnClickListener(v -> {
                    Intent intent = new Intent(ReportsActivity.this, TakeAttendanceActivity.class);
                    intent.putExtra(ClassListActivity.EXTRA_CLASS_ID, session.getClassId());
                    intent.putExtra(ClassListActivity.EXTRA_CLASS_NAME, session.getClassLabel());
                    intent.putExtra(TakeAttendanceActivity.EXTRA_SESSION_DATE, session.getSessionDate());
                    intent.putExtra(LoginActivity.EXTRA_TEACHER_ID, teacherId);
                    startActivity(intent);
                });

                listSessions.addView(row);
            }
        }
    }

    private void exportPdf() {
        Toast.makeText(this, R.string.generating_pdf, Toast.LENGTH_SHORT).show();

        String selectedLabel = getString(R.string.all_classes);
        if (selectedClassId != -1) {
            for (SchoolClass c : teacherClasses) {
                if (c.getId() == selectedClassId) {
                    selectedLabel = c.getLabel();
                    break;
                }
            }
        }

        // Fetch teacher's name
        com.kal.classattendance.data.TeacherDao teacherDao = new com.kal.classattendance.data.TeacherDao(this);
        com.kal.classattendance.model.Teacher teacher = teacherDao.getById(teacherId);
        String teacherName = teacher != null ? teacher.getFullName() : "Instructor";

        // Fetch comprehensive student roster statistics
        List<AttendanceDao.StudentRosterStat> studentStats =
                attendanceDao.getStudentRosterStats(teacherId, selectedClassId);

        PdfReportExporter.exportReport(
                this,
                teacherName,
                selectedLabel,
                currentStats,
                currentSessions,
                studentStats,
                new PdfReportExporter.ExportCallback() {
                    @Override
                    public void onSuccess(File pdfFile, Uri uri) {
                        runOnUiThread(() -> {
                            Toast.makeText(ReportsActivity.this, R.string.pdf_exported, Toast.LENGTH_SHORT).show();
                            openOrSharePdf(uri);
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> Toast.makeText(
                                ReportsActivity.this,
                                getString(R.string.pdf_export_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void openOrSharePdf(Uri uri) {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(uri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(viewIntent, "Open Attendance Report");
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{shareIntent});
        try {
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(this, "No PDF viewer installed", Toast.LENGTH_SHORT).show();
        }
    }
}
