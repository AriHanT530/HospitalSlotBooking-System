package com.hospital.service;

import com.hospital.dao.UserRepository;
import com.hospital.exception.AuthenticationException;
import com.hospital.exception.DataAccessException;
import com.hospital.exception.HospitalException;
import com.hospital.exception.ValidationException;
import com.hospital.model.Admin;
import com.hospital.model.Doctor;
import com.hospital.model.Patient;
import com.hospital.model.Role;
import com.hospital.model.Specialization;
import com.hospital.model.User;
import com.hospital.util.AppLogger;
import com.hospital.util.IdGenerator;
import com.hospital.util.PasswordUtil;
import com.hospital.util.Validator;

import java.time.LocalTime;
import java.util.Optional;

/**
 * Module 1 of the system: user management.
 * Handles registration, login, session state and password changes.
 */
public class AuthService {

    private static final int MAX_ATTEMPTS = 3;

    private final UserRepository users;
    private final AppLogger log = AppLogger.getInstance();
    private User currentUser;
    private int failedAttempts = 0;

    public AuthService(UserRepository users) { this.users = users; }

    public User login(String username, String password) throws HospitalException {
        if (failedAttempts >= MAX_ATTEMPTS) {
            log.warn("Login blocked after " + MAX_ATTEMPTS + " failed attempts for '" + username + "'");
            throw new AuthenticationException("Too many failed attempts. Restart the application to try again.");
        }
        Optional<User> found = users.findByUsername(username);
        if (found.isEmpty() || !PasswordUtil.verify(password, found.get().getPasswordHash())) {
            failedAttempts++;
            log.warn("Failed login for '" + username + "' (attempt " + failedAttempts + ")");
            throw new AuthenticationException("Invalid username or password ("
                    + (MAX_ATTEMPTS - failedAttempts) + " attempt(s) left)");
        }
        User user = found.get();
        if (!user.isActive()) {
            throw new AuthenticationException("This account has been deactivated. Contact the administrator.");
        }
        failedAttempts = 0;
        currentUser = user;
        log.audit("LOGIN " + user.getId() + " as " + user.getRole());
        return user;
    }

    public void logout() {
        if (currentUser != null) {
            log.audit("LOGOUT " + currentUser.getId());
            currentUser = null;
        }
    }

    public User getCurrentUser() { return currentUser; }

    public boolean isLoggedIn() { return currentUser != null; }

    public boolean hasRole(Role role) { return currentUser != null && currentUser.getRole() == role; }

    /** Self-service patient registration from the welcome screen. */
    public Patient registerPatient(String username, String password, String fullName, String phone,
                                   int age, String gender, String bloodGroup) throws HospitalException {
        String uname = Validator.requireUsername(username);
        Validator.requirePassword(password);
        if (users.usernameExists(uname)) {
            throw new AuthenticationException("Username '" + uname + "' is already taken");
        }
        Patient patient = new Patient(IdGenerator.next("PAT"), uname, PasswordUtil.hash(password),
                Validator.requireName(fullName), Validator.requirePhone(phone),
                age, gender, bloodGroup == null || bloodGroup.isBlank() ? "NA" : bloodGroup.toUpperCase());
        users.save(patient);
        log.audit("REGISTER patient " + patient.getId() + " (" + uname + ")");
        return patient;
    }

    /** Admin-only: onboarding a doctor also creates their login. */
    public Doctor registerDoctor(String username, String password, String fullName, String phone,
                                 Specialization specialization, double fee, String roomNo,
                                 LocalTime start, LocalTime end, int slotMinutes) throws HospitalException {
        String uname = Validator.requireUsername(username);
        Validator.requirePassword(password);
        if (users.usernameExists(uname)) {
            throw new AuthenticationException("Username '" + uname + "' is already taken");
        }
        if (!start.isBefore(end)) {
            throw new ValidationException("Work start time must be earlier than end time");
        }
        Doctor doctor = new Doctor(IdGenerator.next("DOC"), uname, PasswordUtil.hash(password),
                Validator.requireName(fullName), Validator.requirePhone(phone),
                specialization, fee, roomNo, start, end, slotMinutes);
        users.save(doctor);
        log.audit("REGISTER doctor " + doctor.getId() + " (" + uname + ")");
        return doctor;
    }

    public Admin registerAdmin(String username, String password, String fullName,
                               String phone, String designation) throws HospitalException {
        String uname = Validator.requireUsername(username);
        Validator.requirePassword(password);
        if (users.usernameExists(uname)) {
            throw new AuthenticationException("Username '" + uname + "' is already taken");
        }
        Admin admin = new Admin(IdGenerator.next("ADM"), uname, PasswordUtil.hash(password),
                Validator.requireName(fullName), Validator.requirePhone(phone), designation);
        users.save(admin);
        log.audit("REGISTER admin " + admin.getId());
        return admin;
    }

    public void changePassword(String oldPassword, String newPassword) throws HospitalException {
        if (currentUser == null) throw new AuthenticationException("No user is logged in");
        if (!PasswordUtil.verify(oldPassword, currentUser.getPasswordHash())) {
            throw new AuthenticationException("Current password is incorrect");
        }
        Validator.requirePassword(newPassword);
        currentUser.setPasswordHash(PasswordUtil.hash(newPassword));
        users.update(currentUser);
        log.audit("PASSWORD_CHANGE " + currentUser.getId());
    }

    public void setActive(String userId, boolean active) throws HospitalException {
        User user = users.findById(userId)
                .orElseThrow(() -> new com.hospital.exception.EntityNotFoundException("User", userId));
        user.setActive(active);
        users.update(user);
        log.audit((active ? "ENABLE " : "DISABLE ") + userId);
    }

    public UserRepository getUserRepository() { return users; }

    /** Convenience used by the seeder. */
    public boolean noUsersYet() throws DataAccessException { return users.count() == 0; }
}
