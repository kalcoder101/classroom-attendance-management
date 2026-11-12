package com.kal.classattendance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kal.classattendance.data.AttendanceDao.StudentRosterStat;

import org.junit.Test;

/** Covers the "View Attendance" summary shown from the student context menu. */
public class StudentAttendanceHistoryTest {

    private static final String CLASS_LABEL = "CS – 2nd Year – Section A";

    @Test
    public void noSessions_saysNothingRecordedYet() {
        String text = ManageStudentsActivity.formatAttendanceHistory(null);
        assertTrue(text.contains("No attendance has been recorded"));

        StudentRosterStat empty = new StudentRosterStat(1L, "S001", "Asha", CLASS_LABEL, 0, 0, 0);
        assertTrue(ManageStudentsActivity.formatAttendanceHistory(empty)
                .contains("No attendance has been recorded"));
    }

    @Test
    public void summary_listsCountsAndRate() {
        StudentRosterStat stat = new StudentRosterStat(1L, "S001", "Asha", CLASS_LABEL, 8, 1, 1);

        String text = ManageStudentsActivity.formatAttendanceHistory(stat);

        assertTrue(text.contains("Class: " + CLASS_LABEL));
        assertTrue(text.contains("Sessions: 10"));
        assertTrue(text.contains("Present: 8"));
        assertTrue(text.contains("Absent: 1"));
        assertTrue(text.contains("Late: 1"));
        // Late counts as attended: (8 present + 1 late) / 10 sessions
        assertTrue(text.contains("Attendance rate: 90.0%"));
        assertFalse(text.contains("At risk"));
    }

    @Test
    public void lowRate_isFlaggedAtRisk() {
        StudentRosterStat stat = new StudentRosterStat(1L, "S001", "Asha", CLASS_LABEL, 6, 4, 0);

        String text = ManageStudentsActivity.formatAttendanceHistory(stat);

        assertTrue(text.contains("Attendance rate: 60.0%"));
        assertTrue(text.contains("At risk"));
    }
}
