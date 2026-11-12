package com.kal.classattendance.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.kal.classattendance.R;
import com.kal.classattendance.data.AttendanceDao.SessionSummary;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Modern canvas-rendered chart view that visualizes attendance percentage trends
 * across historical sessions, complete with smooth line, gradient fill, and data points.
 */
public class AttendanceChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<SessionSummary> sessions = new ArrayList<>();
    private final SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat labelFormat = new SimpleDateFormat("d MMM", Locale.US);

    private int primaryColor;
    private int gridColor;
    private int textColor;
    private int presentColor;
    private int absentColor;
    private int lateColor;

    public AttendanceChartView(Context context) {
        super(context);
        init(context);
    }

    public AttendanceChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AttendanceChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        primaryColor = ContextCompat.getColor(context, R.color.primary_blue);
        gridColor = ContextCompat.getColor(context, R.color.divider_gray);
        textColor = ContextCompat.getColor(context, R.color.text_secondary);
        presentColor = ContextCompat.getColor(context, R.color.present_text);
        absentColor = ContextCompat.getColor(context, R.color.danger_red);
        lateColor = ContextCompat.getColor(context, R.color.late_text);

        linePaint.setColor(primaryColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dpToPx(3));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint.setColor(primaryColor);
        pointPaint.setStyle(Paint.Style.FILL);

        pointInnerPaint.setColor(Color.WHITE);
        pointInnerPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(textColor);
        textPaint.setTextSize(spToPx(11));
        textPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setColor(gridColor);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dpToPx(1));

        barPaint.setStyle(Paint.Style.FILL);
    }

    /** Set sessions to display (chronological left-to-right order up to last 7). */
    public void setSessions(List<SessionSummary> sessionList) {
        sessions.clear();
        if (sessionList != null && !sessionList.isEmpty()) {
            int take = Math.min(sessionList.size(), 7);
            List<SessionSummary> subList = new ArrayList<>(sessionList.subList(0, take));
            Collections.reverse(subList); // Oldest on left, newest on right
            sessions.addAll(subList);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        float paddingLeft = dpToPx(36);
        float paddingRight = dpToPx(20);
        float paddingTop = dpToPx(24);
        float paddingBottom = dpToPx(32);

        float plotWidth = width - paddingLeft - paddingRight;
        float plotHeight = height - paddingTop - paddingBottom;

        // Draw horizontal grid lines (0%, 50%, 100%)
        float[] levels = {1.0f, 0.5f, 0.0f};
        String[] labels = {"100%", "50%", "0%"};
        textPaint.setTextAlign(Paint.Align.RIGHT);

        for (int i = 0; i < levels.length; i++) {
            float y = paddingTop + plotHeight * (1.0f - levels[i]);
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint);
            canvas.drawText(labels[i], paddingLeft - dpToPx(6), y + dpToPx(4), textPaint);
        }

        if (sessions.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(spToPx(14));
            canvas.drawText("No session data to plot yet", width / 2f, height / 2f, textPaint);
            textPaint.setTextSize(spToPx(11));
            return;
        }

        int count = sessions.size();
        float stepX = count > 1 ? plotWidth / (count - 1) : 0;

        float[] xCoords = new float[count];
        float[] yCoords = new float[count];

        for (int i = 0; i < count; i++) {
            SessionSummary s = sessions.get(i);
            float x = count > 1 ? paddingLeft + i * stepX : paddingLeft + plotWidth / 2f;
            float rateRatio = Math.max(0f, Math.min(1f, s.getAttendanceRate() / 100f));
            float y = paddingTop + plotHeight * (1.0f - rateRatio);

            xCoords[i] = x;
            yCoords[i] = y;
        }

        // Draw filled area under line if multiple points
        if (count > 1) {
            Path fillPath = new Path();
            fillPath.moveTo(xCoords[0], yCoords[0]);
            for (int i = 1; i < count; i++) {
                fillPath.lineTo(xCoords[i], yCoords[i]);
            }
            fillPath.lineTo(xCoords[count - 1], paddingTop + plotHeight);
            fillPath.lineTo(xCoords[0], paddingTop + plotHeight);
            fillPath.close();

            fillPaint.setColor(Color.argb(35, 30, 95, 192));
            canvas.drawPath(fillPath, fillPaint);

            // Draw line
            Path linePath = new Path();
            linePath.moveTo(xCoords[0], yCoords[0]);
            for (int i = 1; i < count; i++) {
                linePath.lineTo(xCoords[i], yCoords[i]);
            }
            canvas.drawPath(linePath, linePaint);
        }

        // Draw data points & X-axis labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < count; i++) {
            float x = xCoords[i];
            float y = yCoords[i];

            // Point outer and inner circle
            canvas.drawCircle(x, y, dpToPx(6), pointPaint);
            canvas.drawCircle(x, y, dpToPx(3), pointInnerPaint);

            // Percentage value label above point
            SessionSummary s = sessions.get(i);
            String pct = Math.round(s.getAttendanceRate()) + "%";
            canvas.drawText(pct, x, y - dpToPx(8), textPaint);

            // Date label below axis
            String dateLabel = s.getSessionDate();
            try {
                Date d = inFormat.parse(s.getSessionDate());
                if (d != null) dateLabel = labelFormat.format(d);
            } catch (ParseException ignored) {}
            canvas.drawText(dateLabel, x, height - dpToPx(8), textPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
