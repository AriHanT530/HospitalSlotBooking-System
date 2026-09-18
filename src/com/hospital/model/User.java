package com.hospital.model;

import java.util.Objects;

/**
 * Abstract base of every actor in the system (Unit 2: abstract class,
 * encapsulation, inheritance). Subclasses: Patient, Doctor, Admin.
 *
 * All fields are private and exposed through getters/setters so that
 * validation rules stay inside the object (encapsulation).
 */
public abstract class User {

    private final String id;
    private String username;
    private String passwordHash;
    private String fullName;
    private String phone;
    private boolean active = true;

    protected User(String id, String username, String passwordHash, String fullName, String phone) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
    }

    /** Every concrete user must declare its role. */
    public abstract Role getRole();

    /** Short line used in listings; overridden by subclasses (polymorphism). */
    public String describe() {
        return getRole().getLabel() + " " + fullName + " (" + id + ")";
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return id.equals(((User) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() { return describe(); }
}
