package com.hospital.model;

/** Hospital administrator: manages doctors, patients and reports. */
public class Admin extends User {

    private String designation;

    public Admin(String id, String username, String passwordHash, String fullName,
                 String phone, String designation) {
        super(id, username, passwordHash, fullName, phone);
        this.designation = designation;
    }

    @Override
    public Role getRole() { return Role.ADMIN; }

    @Override
    public String describe() {
        return String.format("%-8s %-22s %s", getId(), getFullName(), designation);
    }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
}
