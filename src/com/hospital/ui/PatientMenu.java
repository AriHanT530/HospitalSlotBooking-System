package com.hospital.ui;

import com.hospital.exception.HospitalException;
import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.Prescription;
import com.hospital.model.Slot;
import com.hospital.model.Specialization;
import com.hospital.service.ServiceRegistry;
import com.hospital.util.ConsoleInput;
import com.hospital.util.TableFormatter;
import com.hospital.util.Validator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Patient-facing screens: find a doctor, see slots, book, cancel, history. */
public class PatientMenu extends RoleMenu {

    public PatientMenu(ServiceRegistry registry) { super(registry); }

    @Override
    protected String title() { return "Patient Menu"; }

    @Override
    protected String[] options() {
        return new String[]{
                "Browse doctors",
                "View available slots",
                "Book an appointment",
                "My appointments",
                "Cancel an appointment",
                "Reschedule an appointment",
                "My prescriptions",
                "Change password"
        };
    }

    @Override
    protected boolean handle(String choice) {
        try {
            switch (choice) {
                case "1" -> browseDoctors();
                case "2" -> viewSlots();
                case "3" -> book();
                case "4" -> myAppointments();
                case "5" -> cancel();
                case "6" -> reschedule();
                case "7" -> myPrescriptions();
                case "8" -> changePassword();
                default  -> TableFormatter.error("Invalid option");
            }
        } catch (HospitalException e) {
            TableFormatter.error(e.getMessage());
        }
        return true;
    }

    private void browseDoctors() throws HospitalException {
        TableFormatter.subHeader("Doctors");
        System.out.println("Filter by specialization (blank = all):");
        for (Specialization s : Specialization.values()) {
            System.out.printf("   %-14s %s (%d min slots)%n", s.name(), s.getDisplayName(), s.getDefaultSlotMinutes());
        }
        String filter = ConsoleInput.ask("\nSpecialization: ");
        List<Doctor> doctors = registry.getUserRepository().findDoctors();
        int shown = 0;
        for (Doctor d : doctors) {
            if (!filter.isBlank() && d.getSpecialization() != Specialization.fromString(filter)) continue;
            if (!d.isActive()) continue;
            System.out.println("  " + d.describe());
            shown++;
        }
        if (shown == 0) TableFormatter.info("No doctors matched that filter.");
    }

    private void viewSlots() throws HospitalException {
        Doctor doctor = pickDoctor();
        if (doctor == null) return;
        LocalDate date = Validator.requireFutureDate(
                ConsoleInput.askOrDefault("Date (yyyy-MM-dd) [today]: ", LocalDate.now().toString()));

        List<Slot> slots = registry.getSlotService().getSlots(doctor, date);
        TableFormatter.subHeader("Dr. " + doctor.getFullName() + " on " + date);
        if (slots.isEmpty()) {
            TableFormatter.info("This doctor has no slots configured for that day.");
            return;
        }
        int i = 1;
        for (Slot slot : slots) {
            System.out.printf("   %2d) %s - %s   %s%n", i++, slot.getStart(), slot.getEnd(), slot.statusLabel());
        }
        System.out.printf("%nOccupancy: %.1f%%%n", registry.getSlotService().occupancyRate(doctor, date));
    }

    private void book() throws HospitalException {
        Doctor doctor = pickDoctor();
        if (doctor == null) return;
        LocalDate date = Validator.requireFutureDate(
                ConsoleInput.askOrDefault("Date (yyyy-MM-dd) [tomorrow]: ",
                        LocalDate.now().plusDays(1).toString()));

        List<Slot> free = registry.getSlotService().getAvailableSlots(doctor, date);
        if (free.isEmpty()) {
            LocalDate next = registry.getSlotService().nextAvailableDate(doctor, date.plusDays(1), 14);
            TableFormatter.info("No free slots on " + date
                    + (next == null ? "." : ". Next free day is " + next + "."));
            return;
        }
        TableFormatter.subHeader("Available slots");
        for (int i = 0; i < free.size(); i++) {
            System.out.printf("   %2d) %s%n", i + 1, free.get(i).getStart());
        }
        int pick = Validator.requireRange(ConsoleInput.ask("\nPick a slot number: "), 1, free.size(), "Slot number");
        String reason = ConsoleInput.askOrDefault("Reason for visit [General consultation]: ", "General consultation");

        LocalTime start = free.get(pick - 1).getStart();
        Appointment appointment = registry.getBookingService()
                .book(currentUser().getId(), doctor.getId(), date, start, reason);

        TableFormatter.success("Booked! Reference " + appointment.getId());
        System.out.printf("   Dr. %s | %s %s | Room %s | Fee INR %.2f%n",
                doctor.getFullName(), date, start, doctor.getRoomNo(), appointment.getFee());
    }

    private void myAppointments() throws HospitalException {
        List<Appointment> list = registry.getAppointmentRepository().findByPatient(currentUser().getId());
        TableFormatter.subHeader("My appointments (" + list.size() + ")");
        if (list.isEmpty()) {
            TableFormatter.info("Nothing booked yet.");
            return;
        }
        for (Appointment a : list) {
            System.out.println("  " + a);
        }
        System.out.printf("%nUpcoming: %d%n", registry.getBookingService().countActive(currentUser().getId()));
    }

    private void cancel() throws HospitalException {
        List<Appointment> upcoming = registry.getBookingService().upcomingForPatient(currentUser().getId());
        if (upcoming.isEmpty()) {
            TableFormatter.info("You have no upcoming appointments to cancel.");
            return;
        }
        upcoming.forEach(a -> System.out.println("  " + a));
        String id = ConsoleInput.ask("\nAppointment id to cancel: ");
        Appointment cancelled = registry.getBookingService().cancel(id.toUpperCase(), currentUser().getId());
        TableFormatter.success("Status is now " + cancelled.getStatus()
                + (cancelled.getStatus().name().equals("NO_SHOW")
                   ? " - cancelled inside the 2 hour window, 50% is chargeable." : "."));
    }

    private void reschedule() throws HospitalException {
        List<Appointment> upcoming = registry.getBookingService().upcomingForPatient(currentUser().getId());
        if (upcoming.isEmpty()) {
            TableFormatter.info("Nothing to reschedule.");
            return;
        }
        upcoming.forEach(a -> System.out.println("  " + a));
        String id = ConsoleInput.ask("\nAppointment id: ").toUpperCase();
        LocalDate date = Validator.requireFutureDate(ConsoleInput.ask("New date (yyyy-MM-dd): "));
        LocalTime time = Validator.requireTime(ConsoleInput.ask("New start time (HH:mm): "));
        Appointment moved = registry.getBookingService().reschedule(id, date, time, currentUser().getId());
        TableFormatter.success("Moved to " + moved.getDate() + " " + moved.getStartTime()
                + " under new reference " + moved.getId());
    }

    private void myPrescriptions() throws HospitalException {
        List<Prescription> history = registry.getPrescriptionService().historyOfPatient(currentUser().getId());
        TableFormatter.subHeader("Prescription history (" + history.size() + ")");
        if (history.isEmpty()) {
            TableFormatter.info("No prescriptions issued yet.");
            return;
        }
        for (Prescription p : history) {
            System.out.println("  " + p.getId() + "  " + p.getIssuedAt().toLocalDate()
                    + "  Diagnosis: " + p.getDiagnosis());
            System.out.println("      Medicines: " + String.join(", ", p.getMedicines()));
            System.out.println("      Advice   : " + p.getAdvice());
        }
    }

    private void changePassword() throws HospitalException {
        String oldPass = ConsoleInput.ask("Current password: ");
        String newPass = ConsoleInput.ask("New password: ");
        registry.getAuthService().changePassword(oldPass, newPass);
        TableFormatter.success("Password updated.");
    }

    private Doctor pickDoctor() throws HospitalException {
        List<Doctor> doctors = registry.getUserRepository().findDoctors();
        if (doctors.isEmpty()) {
            TableFormatter.info("No doctors on record.");
            return null;
        }
        TableFormatter.subHeader("Select a doctor");
        for (int i = 0; i < doctors.size(); i++) {
            System.out.printf("   %2d) %s%n", i + 1, doctors.get(i).describe());
        }
        int pick = Validator.requireRange(ConsoleInput.ask("\nDoctor number: "), 1, doctors.size(), "Doctor number");
        return doctors.get(pick - 1);
    }
}
