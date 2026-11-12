package com.kal.classattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kal.classattendance.data.AttendanceDao;
import com.kal.classattendance.data.AttendanceDao.AttendanceOverviewStats;
import com.kal.classattendance.data.AttendanceDao.SessionSummary;
import com.kal.classattendance.data.AttendanceDao.StudentRosterStat;

import org.junit.Test;

/**
 * The attendance rule is "present or late counts as attended, only absent
 * misses". Every rate in the app (session, overview, roster, at-risk) must use it.
 */
public class AttendanceRateTest {

    private static final float DELTA = 0.001f;

    @Test
    public void helper_countsLateAsAttended() {
        assertEquals(90f, AttendanceDao.attendanceRate(8, 1, 10), DELTA);
        assertEquals(100f, AttendanceDao.attendanceRate(0, 10, 10), DELTA);
        assertEquals(0f, AttendanceDao.attendanceRate(0, 0, 0), DELTA);
    }

    @Test
    public void sessionRate_countsLateAsAttended() {
        SessionSummary summary =
                new SessionSummary(2L, "CS – 2nd Year – Section A", "2026-11-10", 5, 0, 5, 10);
        assertEquals(100f, summary.getAttendanceRate(), DELTA);
    }

    @Test
    public void overviewRate_countsLateAsAttended() {
        AttendanceOverviewStats stats = new AttendanceOverviewStats(4, 6, 2, 2);
        assertEquals(80f, stats.attendanceRate, DELTA);
    }

    @Test
    public void rosterRateAndAtRisk_countLateAsAttended() {
        // Late every session: attended, not at risk
        StudentRosterStat alwaysLate =
                new StudentRosterStat(1L, "S001", "Asha", "CS – 2nd Year – Section A", 0, 0, 10);
        assertEquals(100f, alwaysLate.attendanceRate, DELTA);
        assertFalse(alwaysLate.isAtRisk());

        // 7 present + 0 late of 10 = 70% -> at risk
        StudentRosterStat mostlyAbsent =
                new StudentRosterStat(2L, "S002", "Ravi", "CS – 2nd Year – Section A", 7, 3, 0);
        assertEquals(70f, mostlyAbsent.attendanceRate, DELTA);
        assertTrue(mostlyAbsent.isAtRisk());

        // A late saves a student from the threshold: 7 present + 1 late of 10 = 80%
        StudentRosterStat mostlyPresent =
                new StudentRosterStat(3L, "S003", "Meera", "CS – 2nd Year – Section A", 7, 2, 1);
        assertEquals(80f, mostlyPresent.attendanceRate, DELTA);
        assertFalse(mostlyPresent.isAtRisk());
    }
}
