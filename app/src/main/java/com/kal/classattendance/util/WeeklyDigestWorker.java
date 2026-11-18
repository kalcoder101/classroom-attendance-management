package com.kal.classattendance.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.kal.classattendance.data.AttendanceDao;
import com.kal.classattendance.data.AttendanceDao.AttendanceOverviewStats;
import com.kal.classattendance.data.AttendanceDao.StudentRosterStat;

import java.util.List;

/**
 * Weekly summary: sessions recorded, average attendance rate, and how many
 * students are currently below the at-risk threshold.
 */
public class WeeklyDigestWorker extends Worker {

    public WeeklyDigestWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        long teacherId = new SessionStore(context).getTeacherId();
        if (teacherId == -1) {
            return Result.success();
        }

        AttendanceDao attendanceDao = new AttendanceDao(context);
        AttendanceOverviewStats stats = attendanceDao.getOverviewStats(teacherId, -1);
        if (stats.totalSessions == 0) {
            return Result.success();
        }

        int total = stats.totalPresent + stats.totalAbsent + stats.totalLate;
        int averageRate = Math.round(
                AttendanceDao.attendanceRate(stats.totalPresent, stats.totalLate, total));

        int atRiskCount = 0;
        List<StudentRosterStat> roster = attendanceDao.getStudentRosterStats(teacherId, -1);
        for (StudentRosterStat student : roster) {
            if (student.isAtRisk()) {
                atRiskCount++;
            }
        }

        AttendanceNotifier.notifyDigest(context, teacherId, stats.totalSessions,
                averageRate, atRiskCount);
        return Result.success();
    }
}
