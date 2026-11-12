package com.kal.classattendance.model;

public class SchoolClass {

    private final long id;
    private final long teacherId;
    private final String department;
    private final int year;
    private final int section;
    private final int studentCount;

    public SchoolClass(long id, long teacherId, String department,
                       int year, int section, int studentCount) {
        this.id = id;
        this.teacherId = teacherId;
        this.department = department;
        this.year = year;
        this.section = section;
        this.studentCount = studentCount;
    }

    public long getId() { return id; }

    public long getTeacherId() { return teacherId; }

    public String getDepartment() { return department; }

    public int getYear() { return year; }

    public int getSection() { return section; }

    public int getStudentCount() { return studentCount; }

    /** Human-readable label, e.g. "CS – 4th Year – Section 1" */
    public String getLabel() {
        return department + " – " + yearLabel(year) + " Year – Section " + section;
    }

    /** "1st", "2nd", "3rd", "4th", ... */
    public static String yearLabel(int year) {
        switch (year) {
            case 1: return "1st";
            case 2: return "2nd";
            case 3: return "3rd";
            default: return year + "th";
        }
    }
}
