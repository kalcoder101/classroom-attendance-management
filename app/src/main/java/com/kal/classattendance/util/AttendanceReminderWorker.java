package com.kal.classattendance.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.kal.classattendance.data.AttendanceDao;
import com.kal.classattendance.data.ClassDao;
import com.kal.classattendance.model.SchoolClass;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Daily check: if any of the logged-in teacher's classes still have no
 * attendance for today, post one reminder listing them.
 */
public class AttendanceReminderWorker extends Worker {

    public AttendanceReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
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

        List<SchoolClass> classes = new ClassDao(context).listForTeacher(teacherId);
        if (classes.isEmpty()) {
            return Result.success();
        }

        String today = Dates.dbKey(new Date());
        AttendanceDao attendanceDao = new AttendanceDao(context);

        List<SchoolClass> missing = new ArrayList<>();
        for (SchoolClass schoolClass : classes) {
            if (attendanceDao.findSessionId(schoolClass.getId(), today) == -1) {
                missing.add(schoolClass);
            }
        }

        if (!missing.isEmpty()) {
            AttendanceNotifier.notifyMissingAttendance(context, teacherId, missing);
        }
        return Result.success();
    }
}
