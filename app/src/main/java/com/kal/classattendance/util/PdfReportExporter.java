package com.kal.classattendance.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.kal.classattendance.data.AttendanceDao.AttendanceOverviewStats;
import com.kal.classattendance.data.AttendanceDao.SessionSummary;
import com.kal.classattendance.data.AttendanceDao.StudentRosterStat;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Generates an elegant, professional multi-page A4 PDF attendance report
 * complete with summary KPIs, session history, comprehensive student roster
 * with low-attendance warnings (<75%), and an official teacher signature block.
 */
public class PdfReportExporter {

    public interface ExportCallback {
        void onSuccess(File pdfFile, Uri uri);
        void onError(Exception e);
    }

    public static void exportReport(Context context,
                                    String teacherName,
                                    String filterClassName,
                                    AttendanceOverviewStats stats,
                                    List<SessionSummary> sessions,
                                    List<StudentRosterStat> studentStats,
                                    ExportCallback callback) {
        new Thread(() -> {
            PdfDocument document = new PdfDocument();
            try {
                final int pageWidth = 595;   // A4 points width at 72 dpi
                final int pageHeight = 842;  // A4 points height at 72 dpi
                final int margin = 36;
                final int contentWidth = pageWidth - (margin * 2);

                final int brandBlue = Color.parseColor("#1E5FC0");
                final int darkText = Color.parseColor("#1F2633");
                final int secondaryText = Color.parseColor("#5F6878");
                final int lightBg = Color.parseColor("#F7F9FC");
                final int borderGray = Color.parseColor("#D9DEE8");
                final int presentGreen = Color.parseColor("#1E6B3A");
                final int absentRed = Color.parseColor("#C62828");
                final int lateOrange = Color.parseColor("#B26A00");
                final int warningBg = Color.parseColor("#FDE8E8");

                String generatedOn = "Generated: " + new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(new Date());
                String teacherStr = teacherName != null && !teacherName.isEmpty() ? teacherName : "Teacher";

                // We plan pages:
                // Page 1: Top header, 4 KPI cards, Recent Session History table, and start of Student Roster.
                // Page 2+ (if needed): Continuing Student Roster + Official Signature Block.

                int currentPageNumber = 1;
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                // Top decorative blue strip
                paint.setColor(brandBlue);
                canvas.drawRect(0, 0, pageWidth, 8, paint);

                int y = 46;

                // Document Title
                paint.setColor(brandBlue);
                paint.setTextSize(20);
                paint.setFakeBoldText(true);
                canvas.drawText("Class Attendance Official Report", margin, y, paint);

                // Generation date (top right)
                paint.setFakeBoldText(false);
                paint.setTextSize(9);
                paint.setColor(secondaryText);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(generatedOn, pageWidth - margin, y - 4, paint);
                paint.setTextAlign(Paint.Align.LEFT);

                y += 20;

                // Subtitle metadata
                paint.setTextSize(10);
                paint.setColor(darkText);
                canvas.drawText("Instructor: " + teacherStr + "   •   Class Scope: " + filterClassName, margin, y, paint);

                y += 14;
                paint.setColor(borderGray);
                paint.setStrokeWidth(1);
                canvas.drawLine(margin, y, pageWidth - margin, y, paint);

                y += 16;

                // 2. Summary KPI Cards
                int cardWidth = (contentWidth - 24) / 4;
                int cardHeight = 52;

                drawMetricCard(canvas, margin, y, cardWidth, cardHeight, "OVERALL RATE",
                        Math.round(stats.attendanceRate) + "%", brandBlue);
                drawMetricCard(canvas, margin + cardWidth + 8, y, cardWidth, cardHeight, "TOTAL SESSIONS",
                        String.valueOf(stats.totalSessions), darkText);
                drawMetricCard(canvas, margin + (cardWidth + 8) * 2, y, cardWidth, cardHeight, "TOTAL PRESENT",
                        String.valueOf(stats.totalPresent), presentGreen);
                drawMetricCard(canvas, margin + (cardWidth + 8) * 3, y, cardWidth, cardHeight, "TOTAL ABSENT",
                        String.valueOf(stats.totalAbsent), absentRed);

                y += cardHeight + 20;

                // 3. Section Title: Recent Sessions Summary (Top 5 sessions)
                paint.setTextSize(12);
                paint.setColor(darkText);
                paint.setFakeBoldText(true);
                canvas.drawText("Recent Attendance Sessions", margin, y, paint);
                paint.setFakeBoldText(false);

                y += 10;

                // Session table header
                int sTableTop = y;
                paint.setColor(lightBg);
                canvas.drawRoundRect(new RectF(margin, sTableTop, pageWidth - margin, sTableTop + 20), 4, 4, paint);

                paint.setColor(secondaryText);
                paint.setTextSize(9);
                paint.setFakeBoldText(true);

                int sColDate = margin + 8;
                int sColClass = margin + 95;
                int sColP = margin + 280;
                int sColA = margin + 340;
                int sColL = margin + 395;
                int sColRate = margin + 450;

                canvas.drawText("DATE", sColDate, sTableTop + 14, paint);
                canvas.drawText("CLASS", sColClass, sTableTop + 14, paint);
                canvas.drawText("PRESENT", sColP, sTableTop + 14, paint);
                canvas.drawText("ABSENT", sColA, sTableTop + 14, paint);
                canvas.drawText("LATE", sColL, sTableTop + 14, paint);
                canvas.drawText("RATE", sColRate, sTableTop + 14, paint);
                paint.setFakeBoldText(false);

                y = sTableTop + 22;

                int sessionsToShow = Math.min(sessions.size(), 4);
                paint.setTextSize(9);

                if (sessions.isEmpty()) {
                    y += 18;
                    paint.setColor(secondaryText);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("No recorded attendance sessions found.", pageWidth / 2f, y, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                    y += 8;
                } else {
                    int rowH = 18;
                    for (int i = 0; i < sessionsToShow; i++) {
                        SessionSummary s = sessions.get(i);
                        if (i % 2 == 1) {
                            paint.setColor(Color.parseColor("#FAFBFC"));
                            canvas.drawRect(margin, y, pageWidth - margin, y + rowH, paint);
                        }

                        paint.setColor(darkText);
                        canvas.drawText(s.getSessionDate(), sColDate, y + 13, paint);

                        String cLabel = s.getClassLabel();
                        if (cLabel.length() > 28) cLabel = cLabel.substring(0, 26) + "...";
                        canvas.drawText(cLabel, sColClass, y + 13, paint);

                        paint.setColor(presentGreen);
                        canvas.drawText(String.valueOf(s.getPresentCount()), sColP + 6, y + 13, paint);

                        paint.setColor(absentRed);
                        canvas.drawText(String.valueOf(s.getAbsentCount()), sColA + 6, y + 13, paint);

                        paint.setColor(lateOrange);
                        canvas.drawText(String.valueOf(s.getLateCount()), sColL + 4, y + 13, paint);

                        paint.setColor(brandBlue);
                        paint.setFakeBoldText(true);
                        canvas.drawText(Math.round(s.getAttendanceRate()) + "%", sColRate + 4, y + 13, paint);
                        paint.setFakeBoldText(false);

                        paint.setColor(borderGray);
                        canvas.drawLine(margin, y + rowH, pageWidth - margin, y + rowH, paint);
                        y += rowH;
                    }
                }

                y += 18;

                // 4. Section Title: Comprehensive Student Roster Breakdown
                paint.setTextSize(12);
                paint.setColor(darkText);
                paint.setFakeBoldText(true);
                canvas.drawText("Student Attendance Roster & Individual Rates", margin, y, paint);
                paint.setFakeBoldText(false);

                y += 10;

                // Student Table Header function
                int colId = margin + 8;
                int colName = margin + 70;
                int colStClass = margin + 220;
                int colStP = margin + 355;
                int colStA = margin + 395;
                int colStL = margin + 435;
                int colStRate = margin + 475;

                drawStudentTableHeader(canvas, margin, y, pageWidth, contentWidth, lightBg, secondaryText,
                        colId, colName, colStClass, colStP, colStA, colStL, colStRate);

                y += 22;

                int studentRowH = 18;
                int studentIndex = 0;
                int totalStudents = studentStats != null ? studentStats.size() : 0;

                while (studentIndex < totalStudents) {
                    // Check if we reached bottom of current page
                    if (y + studentRowH + 60 > pageHeight) {
                        // Draw footer for this page
                        drawPageFooter(canvas, pageWidth, pageHeight, currentPageNumber);
                        document.finishPage(page);

                        currentPageNumber++;
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();

                        // Top bar
                        paint.setColor(brandBlue);
                        canvas.drawRect(0, 0, pageWidth, 6, paint);

                        y = 36;
                        // Continued header
                        paint.setColor(darkText);
                        paint.setTextSize(11);
                        paint.setFakeBoldText(true);
                        canvas.drawText("Student Attendance Roster (Continued)", margin, y, paint);
                        paint.setFakeBoldText(false);

                        y += 14;
                        drawStudentTableHeader(canvas, margin, y, pageWidth, contentWidth, lightBg, secondaryText,
                                colId, colName, colStClass, colStP, colStA, colStL, colStRate);
                        y += 22;
                    }

                    StudentRosterStat st = studentStats.get(studentIndex);

                    // Row background
                    if (st.isAtRisk()) {
                        paint.setColor(warningBg); // Highlight low attendance
                        canvas.drawRect(margin, y, pageWidth - margin, y + studentRowH, paint);
                    } else if (studentIndex % 2 == 1) {
                        paint.setColor(Color.parseColor("#FAFBFC"));
                        canvas.drawRect(margin, y, pageWidth - margin, y + studentRowH, paint);
                    }

                    paint.setTextSize(9);
                    paint.setColor(darkText);

                    // Student ID
                    paint.setFakeBoldText(true);
                    canvas.drawText(st.studentCode, colId, y + 13, paint);
                    paint.setFakeBoldText(false);

                    // Name
                    String sName = st.fullName;
                    if (sName.length() > 22) sName = sName.substring(0, 20) + "...";
                    canvas.drawText(sName, colName, y + 13, paint);

                    // Class
                    String sCls = st.classLabel;
                    if (sCls.length() > 20) sCls = sCls.substring(0, 18) + "...";
                    canvas.drawText(sCls, colStClass, y + 13, paint);

                    // P / A / L
                    paint.setColor(presentGreen);
                    canvas.drawText(String.valueOf(st.presentCount), colStP + 4, y + 13, paint);

                    paint.setColor(absentRed);
                    canvas.drawText(String.valueOf(st.absentCount), colStA + 4, y + 13, paint);

                    paint.setColor(lateOrange);
                    canvas.drawText(String.valueOf(st.lateCount), colStL + 4, y + 13, paint);

                    // Rate
                    if (st.isAtRisk()) {
                        paint.setColor(absentRed);
                        paint.setFakeBoldText(true);
                        canvas.drawText(Math.round(st.attendanceRate) + "% ⚠", colStRate, y + 13, paint);
                    } else {
                        paint.setColor(brandBlue);
                        paint.setFakeBoldText(true);
                        canvas.drawText(Math.round(st.attendanceRate) + "%", colStRate, y + 13, paint);
                    }
                    paint.setFakeBoldText(false);

                    // Divider
                    paint.setColor(borderGray);
                    canvas.drawLine(margin, y + studentRowH, pageWidth - margin, y + studentRowH, paint);

                    y += studentRowH;
                    studentIndex++;
                }

                if (totalStudents == 0) {
                    y += 18;
                    paint.setColor(secondaryText);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("No enrolled students found for this class filter.", pageWidth / 2f, y, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                    y += 12;
                }

                // 5. Official Verification & Signature Block
                y += 24;
                if (y + 70 > pageHeight) {
                    drawPageFooter(canvas, pageWidth, pageHeight, currentPageNumber);
                    document.finishPage(page);

                    currentPageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    paint.setColor(brandBlue);
                    canvas.drawRect(0, 0, pageWidth, 6, paint);
                    y = 50;
                }

                drawSignatureBlock(canvas, margin, y, contentWidth, teacherStr);

                // Footer for final page
                drawPageFooter(canvas, pageWidth, pageHeight, currentPageNumber);
                document.finishPage(page);

                // Write file to cache
                File reportsDir = new File(context.getCacheDir(), "reports");
                if (!reportsDir.exists()) reportsDir.mkdirs();

                String filename = "Attendance_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
                File pdfFile = new File(reportsDir, filename);

                FileOutputStream fos = new FileOutputStream(pdfFile);
                document.writeTo(fos);
                fos.close();
                document.close();

                Uri fileUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        pdfFile
                );

                if (callback != null) {
                    callback.onSuccess(pdfFile, fileUri);
                }
            } catch (Exception e) {
                document.close();
                if (callback != null) {
                    callback.onError(e);
                }
            }
        }).start();
    }

    private static void drawStudentTableHeader(Canvas canvas, int margin, int y, int pageWidth, int contentWidth,
                                               int lightBg, int secondaryText, int colId, int colName,
                                               int colStClass, int colStP, int colStA, int colStL, int colStRate) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(lightBg);
        canvas.drawRoundRect(new RectF(margin, y, pageWidth - margin, y + 20), 4, 4, paint);

        paint.setColor(secondaryText);
        paint.setTextSize(9);
        paint.setFakeBoldText(true);

        canvas.drawText("STUDENT ID", colId, y + 14, paint);
        canvas.drawText("STUDENT NAME", colName, y + 14, paint);
        canvas.drawText("CLASS", colStClass, y + 14, paint);
        canvas.drawText("P", colStP + 4, y + 14, paint);
        canvas.drawText("A", colStA + 4, y + 14, paint);
        canvas.drawText("L", colStL + 4, y + 14, paint);
        canvas.drawText("RATE", colStRate, y + 14, paint);
    }

    private static void drawSignatureBlock(Canvas canvas, int margin, int y, int contentWidth, String teacherName) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int borderGray = Color.parseColor("#D9DEE8");
        int darkText = Color.parseColor("#1F2633");
        int secondaryText = Color.parseColor("#5F6878");

        p.setColor(borderGray);
        p.setStrokeWidth(1);
        canvas.drawLine(margin, y, margin + contentWidth, y, p);

        y += 22;
        p.setTextSize(9);
        p.setColor(secondaryText);
        p.setFakeBoldText(true);
        canvas.drawText("VERIFICATION & SIGNATURES", margin, y, p);
        p.setFakeBoldText(false);

        y += 28;

        // Teacher signature line
        int col1X = margin;
        int lineWidth = 180;
        p.setColor(darkText);
        canvas.drawLine(col1X, y, col1X + lineWidth, y, p);
        p.setColor(secondaryText);
        p.setTextSize(9);
        canvas.drawText("Instructor Signature: " + teacherName, col1X, y + 12, p);

        // Date signature line
        int col2X = margin + contentWidth - lineWidth;
        p.setColor(darkText);
        canvas.drawLine(col2X, y, col2X + lineWidth, y, p);
        p.setColor(secondaryText);
        p.setTextSize(9);
        canvas.drawText("Date: ____________________", col2X, y + 12, p);
    }

    private static void drawPageFooter(Canvas canvas, int pageWidth, int pageHeight, int pageNum) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#5F6878"));
        p.setTextSize(8);
        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Class Attendance Management System • Official Record", 36, pageHeight - 16, p);

        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Page " + pageNum, pageWidth - 36, pageHeight - 16, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawMetricCard(Canvas canvas, int x, int y, int w, int h,
                                       String title, String value, int valueColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Card background
        p.setColor(Color.parseColor("#F7F9FC"));
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), 5, 5, p);

        // Border
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.parseColor("#D9DEE8"));
        p.setStrokeWidth(1);
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), 5, 5, p);
        p.setStyle(Paint.Style.FILL);

        // Title
        p.setColor(Color.parseColor("#5F6878"));
        p.setTextSize(7.5f);
        p.setFakeBoldText(true);
        canvas.drawText(title, x + 8, y + 15, p);

        // Value
        p.setColor(valueColor);
        p.setTextSize(16);
        p.setFakeBoldText(true);
        canvas.drawText(value, x + 8, y + 38, p);
    }
}
