package com.kal.classattendance.model;

public class Teacher {

    private final long id;
    private final String fullName;
    private final String username;

    public Teacher(long id, String fullName, String username) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
    }

    public long getId() { return id; }

    public String getFullName() { return fullName; }

    public String getUsername() { return username; }
}