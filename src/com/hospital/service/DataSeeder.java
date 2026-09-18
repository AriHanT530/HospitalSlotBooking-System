package com.hospital.service;

import com.hospital.exception.HospitalException;
import com.hospital.model.Specialization;
import com.hospital.util.AppLogger;

import java.time.LocalTime;

/**
 * Creates a usable demo dataset on first run so the application can be
 * evaluated without any manual setup. Runs only when the user store is empty.
 */
public final class DataSeeder {

    private DataSeeder() { }

    public static void seedIfEmpty(AuthService auth) {
        AppLogger log = AppLogger.getInstance();
        try {
            if (!auth.noUsersYet()) return;

            auth.registerAdmin("admin", "admin123", "Ramesh Iyer", "9812345670", "Hospital Administrator");

            auth.registerDoctor("dr.mehta", "doctor123", "Anita Mehta", "9812345671",
                    Specialization.CARDIOLOGY, 800, "C-201",
                    LocalTime.of(9, 0), LocalTime.of(13, 0), 30);
            auth.registerDoctor("dr.rao", "doctor123", "Sunil Rao", "9812345672",
                    Specialization.ORTHOPAEDICS, 650, "B-104",
                    LocalTime.of(10, 0), LocalTime.of(16, 0), 30);
            auth.registerDoctor("dr.khan", "doctor123", "Farah Khan", "9812345673",
                    Specialization.PAEDIATRICS, 500, "A-012",
                    LocalTime.of(9, 30), LocalTime.of(14, 30), 20);
            auth.registerDoctor("dr.nair", "doctor123", "Vishal Nair", "9812345674",
                    Specialization.GENERAL, 300, "A-001",
                    LocalTime.of(8, 0), LocalTime.of(12, 0), 15);

            auth.registerPatient("arjun", "patient123", "Arjun Sharma", "9876543210", 24, "M", "O+");
            auth.registerPatient("priya", "patient123", "Priya Verma", "9876543211", 31, "F", "B+");
            auth.registerPatient("kabir", "patient123", "Kabir Singh", "9876543212", 45, "M", "A+");

            log.info("Seed data created (1 admin, 4 doctors, 3 patients)");
            System.out.println("[i] First run detected - demo accounts created. See README for credentials.");
        } catch (HospitalException e) {
            log.error("Seeding failed", e);
        }
    }
}
