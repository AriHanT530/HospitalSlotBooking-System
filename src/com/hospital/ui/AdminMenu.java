package com.hospital.ui;

import com.hospital.exception.HospitalException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.model.Doctor;
import com.hospital.model.Patient;
import com.hospital.model.Specialization;
import com.hospital.service.ServiceRegistry;
import com.hospital.util.AppLogger;
import com.hospital.util.ConsoleInput;
import com.hospital.util.TableFormatter;
import com.hospital.util.Validator;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Administrator screens: onboarding, oversight, analytics, export, logs. */
public class AdminMenu extends RoleMenu {

    public AdminMenu(ServiceRegistry registry) { super(registry); }

    @Override
    protected String title() { return "Admin Menu"; }

    @Override
    protected String[] options() {
        return new String[]{
                "Add a doctor",
                "List doctors",
                "List patients",
                "Enable / disable an account",
                "All appointments for a date",
                "Analytics dashboard",
                "Export appointments to CSV",
                "View recent system log",
                "Change password"
        };
    }

    @Override
    protected boolean handle(String choice) {
        try {
            switch (choice) {
                case "1" -> addDoctor();
                case "2" -> listDoctors();
                case "3" -> listPatients();
                case "4" -> toggleAccount();
                case "5" -> appointmentsByDate();
                case "6" -> dashboard();
                case "7" -> export();
                case "8" -> viewLog();
                case "9" -> changePassword();
                default  -> TableFormatter.error("Invalid option");
            }
        } catch (HospitalException e) {
            TableFormatter.error(e.getMessage());
        }
        return true;
    }

    private void addDoctor() throws HospitalException {
        TableFormatter.subHeader("Onboard a doctor");
        String username = ConsoleInput.ask("Login username: ");
        String password = ConsoleInput.ask("Temporary password: ");
        String name = ConsoleInput.ask("Full name: ");
        String phone = ConsoleInput.ask("Phone: ");

        System.out.println("Specializations: ");
        Specialization[] values = Specialization.values();
        for (int i = 0; i < values.length; i++) {
            System.out.printf("   %d) %-16s default slot %d min%n",
                    i + 1, values[i].getDisplayName(), values[i].getDefaultSlotMinutes());
        }
        int pick = Validator.requireRange(ConsoleInput.ask("Choose: "), 1, values.length, "Specialization");
        Specialization spec = values[pick - 1];

        double fee = Validator.requireFee(ConsoleInput.ask("Consultation fee (INR): "));
        String room = ConsoleInput.askOrDefault("Room number [NA]: ", "NA");
        LocalTime start = Validator.requireTime(ConsoleInput.askOrDefault("Work start HH:mm [09:00]: ", "09:00"));
        LocalTime end = Validator.requireTime(ConsoleInput.askOrDefault("Work end HH:mm [17:00]: ", "17:00"));
        int slotMinutes = Validator.requireRange(
                ConsoleInput.askOrDefault("Slot length in minutes [" + spec.getDefaultSlotMinutes() + "]: ",
                        String.valueOf(spec.getDefaultSlotMinutes())), 5, 120, "Slot length");

        Doctor doctor = registry.getAuthService().registerDoctor(username, password, name, phone,
                spec, fee, room, start, end, slotMinutes);
        TableFormatter.success("Created " + doctor.getId() + " with " + doctor.slotsPerDay() + " slots per day.");
    }

    private void listDoctors() throws HospitalException {
        List<Doctor> doctors = registry.getUserRepository().findDoctors();
        TableFormatter.subHeader("Doctors (" + doctors.size() + ")");
        for (Doctor d : doctors) {
            System.out.println("  " + d.describe() + (d.isActive() ? "" : "   [DISABLED]"));
        }
    }

    private void listPatients() throws HospitalException {
        List<Patient> patients = registry.getUserRepository().findPatients();
        TableFormatter.subHeader("Patients (" + patients.size() + ")");
        for (Patient p : patients) {
            System.out.println("  " + p.describe() + (p.isActive() ? "" : "   [DISABLED]"));
        }
    }

    private void toggleAccount() throws HospitalException {
        String id = ConsoleInput.ask("User id (e.g. DOC0002 / PAT0001): ").toUpperCase();
        String action = ConsoleInput.ask("Enable or disable? (e/d): ").toLowerCase();
        boolean enable = action.startsWith("e");
        registry.getAuthService().setActive(id, enable);
        TableFormatter.success(id + " is now " + (enable ? "ACTIVE" : "DISABLED"));
    }

    private void appointmentsByDate() throws HospitalException {
        LocalDate date = Validator.requireDate(
                ConsoleInput.askOrDefault("Date (yyyy-MM-dd) [today]: ", LocalDate.now().toString()));
        List<Appointment> list = registry.getAppointmentRepository().findByDate(date);
        TableFormatter.subHeader("Appointments on " + date + " (" + list.size() + ")");
        if (list.isEmpty()) {
            TableFormatter.info("Nothing scheduled.");
            return;
        }
        for (Appointment a : list) System.out.println("  " + a);
    }

    private void dashboard() throws HospitalException {
        var reports = registry.getReportService();
        TableFormatter.subHeader("Status breakdown");
        for (Map.Entry<AppointmentStatus, Integer> e : reports.statusBreakdown().entrySet()) {
            System.out.printf("   %-12s %4d  %s%n", e.getKey(), e.getValue(), "#".repeat(Math.min(40, e.getValue())));
        }

        TableFormatter.subHeader("Revenue by doctor");
        for (Map.Entry<String, Double> e : reports.revenueByDoctor().entrySet()) {
            String name = registry.getUserRepository().findDoctorById(e.getKey())
                    .map(d -> "Dr. " + d.getFullName()).orElse(e.getKey());
            TableFormatter.money("   " + name, e.getValue());
        }

        TableFormatter.subHeader("Demand by specialization");
        reports.demandBySpecialization().forEach((spec, count) ->
                System.out.printf("   %-18s %d%n", spec.getDisplayName(), count));

        TableFormatter.subHeader("Daily load");
        reports.dailyLoad().forEach((date, count) ->
                System.out.printf("   %s  %2d  %s%n", date, count, "*".repeat(Math.min(40, count))));

        TableFormatter.subHeader("Top doctors");
        List<String> top = reports.topDoctors(5);
        if (top.isEmpty()) TableFormatter.info("   No completed consultations yet.");
        top.forEach(line -> System.out.println("   " + line));

        TableFormatter.subHeader("Totals");
        TableFormatter.money("   Gross billable", reports.totalRevenue());
        System.out.printf("   %-28s %.1f%%%n", "Cancellation + no-show rate", reports.cancellationRate());
    }

    private void export() throws HospitalException {
        Path path = registry.getReportService().exportAppointmentsCsv();
        TableFormatter.success("Report written to " + path.toAbsolutePath());
    }

    private void viewLog() {
        TableFormatter.subHeader("Last 20 log entries");
        AppLogger.getInstance().tail(20).forEach(line -> System.out.println("   " + line));
    }

    private void changePassword() throws HospitalException {
        String oldPass = ConsoleInput.ask("Current password: ");
        String newPass = ConsoleInput.ask("New password: ");
        registry.getAuthService().changePassword(oldPass, newPass);
        TableFormatter.success("Password updated.");
    }
}
