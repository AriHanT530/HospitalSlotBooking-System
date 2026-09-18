package com.hospital.model;

/** A patient who books slots. Extends User (single inheritance). */
public class Patient extends User {

    private int age;
    private String gender;
    private String bloodGroup;

    public Patient(String id, String username, String passwordHash, String fullName,
                   String phone, int age, String gender, String bloodGroup) {
        super(id, username, passwordHash, fullName, phone);
        this.age = age;
        this.gender = gender;
        this.bloodGroup = bloodGroup;
    }

    @Override
    public Role getRole() { return Role.PATIENT; }

    /** Method overriding (Unit 2) - richer description than the base class. */
    @Override
    public String describe() {
        return String.format("%-8s %-22s %3d/%-6s  Blood: %-4s  Ph: %s",
                getId(), getFullName(), age, gender, bloodGroup, getPhone());
    }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
}
