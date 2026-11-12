package com.kal.classattendance.model;

public class Teacher {

    private final long id;
    private final String fullName;

    public Teacher(long id, String fullName) {
        this.id = id;
        this.fullName = fullName;
    }

    public long getId() { return id; }

    public String getFullName() { return fullName; }
}