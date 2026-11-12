package com.kal.classattendance.model;

public class Student {

    private final long id;
    private final long classId;
    private final String studentCode;
    private final String fullName;
    private final String classLabel; // joined from classes, may be null when not joined

    public Student(long id, long classId, String studentCode, String fullName, String classLabel) {
        this.id = id;
        this.classId = classId;
        this.studentCode = studentCode;
        this.fullName = fullName;
        this.classLabel = classLabel;
    }

    public long getId() { return id; }

    public long getClassId() { return classId; }

    public String getStudentCode() { return studentCode; }

    public String getFullName() { return fullName; }

    public String getClassLabel() { return classLabel; }

    /** e.g. "S-001 · CS – 4th Year – Section 1" */
    public String getInfoLine() {
        if (classLabel == null) {
            return studentCode;
        }
        return studentCode + " · " + classLabel;
    }
}
