package com.hospital.model;

/** System roles. Drives menu routing and authorisation checks. */
public enum Role {
    ADMIN("Administrator"),
    DOCTOR("Doctor"),
    PATIENT("Patient");

    private final String label;

    Role(String label) { this.label = label; }

    public String getLabel() { return label; }
}
