package com.hospital.ui;

import com.hospital.exception.HospitalException;
import com.hospital.model.Role;
import com.hospital.model.User;
import com.hospital.service.AuthService;
import com.hospital.service.ServiceRegistry;
import com.hospital.util.AppLogger;
import com.hospital.util.ConsoleInput;
import com.hospital.util.TableFormatter;
import com.hospital.util.Validator;

/**
 * Top-level screen: welcome menu, login, and routing to the role menu.
 * The UI layer only formats and delegates - no business rule lives here.
 */
public class ConsoleApp {

    private final ServiceRegistry registry;
    private final AuthService auth;

    public ConsoleApp(ServiceRegistry registry) {
        this.registry = registry;
        this.auth = registry.getAuthService();
    }

    public void run() {
        banner();
        boolean running = true;
        while (running) {
            TableFormatter.header("Welcome");
            System.out.println("  1. Login");
            System.out.println("  2. Register as a new patient");
            System.out.println("  3. About this system");
            System.out.println("  0. Exit");
            String choice = ConsoleInput.ask("\nSelect an option: ");
            switch (choice) {
                case "1" -> doLogin();
                case "2" -> doRegister();
                case "3" -> about();
                case "0" -> running = false;
                default  -> TableFormatter.error("Invalid option, please choose 0-3");
            }
        }
        System.out.println("\nThank you for using the Hospital Slot Booking System.");
    }

    private void banner() {
        System.out.println();
        System.out.println(TableFormatter.line('*'));
        System.out.println("   HOSPITAL APPOINTMENT & SLOT BOOKING SYSTEM");
        System.out.println("   CSE2006 - Programming in Java | Console Application");
        System.out.println(TableFormatter.line('*'));
    }

    private void doLogin() {
        TableFormatter.header("Login");
        String username = ConsoleInput.ask("Username: ");
        String password = ConsoleInput.ask("Password: ");
        try {
            User user = auth.login(username, password);
            TableFormatter.success("Welcome, " + user.getFullName() + " (" + user.getRole().getLabel() + ")");
            routeTo(user);
        } catch (HospitalException e) {
            TableFormatter.error(e.getMessage());
        }
    }

    /** Runtime polymorphism decides which menu the session lands in. */
    private void routeTo(User user) {
        RoleMenu menu;
        if (user.getRole() == Role.ADMIN) {
            menu = new AdminMenu(registry);
        } else if (user.getRole() == Role.DOCTOR) {
            menu = new DoctorMenu(registry);
        } else {
            menu = new PatientMenu(registry);
        }
        menu.show();
        auth.logout();
        TableFormatter.info("Logged out.");
    }

    private void doRegister() {
        TableFormatter.header("Patient Registration");
        try {
            String username = ConsoleInput.ask("Choose a username (3-20 chars): ");
            String password = ConsoleInput.ask("Choose a password (min 6 chars): ");
            String name = ConsoleInput.ask("Full name: ");
            String phone = ConsoleInput.ask("Phone (10 digits): ");
            int age = Validator.requireAge(ConsoleInput.ask("Age: "));
            String gender = Validator.requireGender(ConsoleInput.ask("Gender (M/F/O): "));
            String blood = ConsoleInput.askOrDefault("Blood group (optional): ", "NA");

            var patient = auth.registerPatient(username, password, name, phone, age, gender, blood);
            TableFormatter.success("Registered. Your patient id is " + patient.getId()
                    + ". You can log in now.");
        } catch (HospitalException e) {
            TableFormatter.error(e.getMessage());
        }
    }

    private void about() {
        TableFormatter.header("About");
        System.out.println("""
                  Three functional modules:
                    1. User management      - registration, login, roles, account state
                    2. Slot & booking       - slot generation, conflict-free booking,
                                              cancellation, rescheduling
                    3. Records & analytics  - prescriptions, revenue and load reports,
                                              CSV export

                  Background threads:
                    reminder-daemon  - logs reminders for appointments due within 24h
                    auto-expiry      - closes slots that elapsed while still BOOKED

                  Storage: pipe-delimited files under data/, with an optional JDBC
                  backend configured through config/db.properties.
                """);
        AppLogger.getInstance().info("About screen viewed");
        ConsoleInput.pause();
    }
}
