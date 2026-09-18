package com.hospital.ui;

import com.hospital.exception.HospitalException;
import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.Prescription;
import com.hospital.model.Slot;
import com.hospital.service.ServiceRegistry;
import com.hospital.util.ConsoleInput;
import com.hospital.util.TableFormatter;
import com.hospital.util.Validator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Doctor-facing screens: day schedule, completion, prescriptions, earnings. */
public class DoctorMenu extends RoleMenu {

    public DoctorMenu(ServiceRegistry registry) { super(registry); }

    @Override
    protected String title() { return "Doctor Menu"; }

    @Override
    protected String[] options() {
        return new String[]{
                "Today's schedule",
                "Schedule for a chosen date",
                "Mark an appointment completed",
                "Issue a prescription",
                "Cancel an appointment",
                "My earnings summary",
                "Change password"
        };
    }

    private Doctor me() { return (Doctor) currentUser(); }

    @Override
    protected boolean handle(String choice) {
        try {
            switch (choice) {
                case "1" -> schedule(LocalDate.now());
                case "2" -> schedule(Validator.requireDate(ConsoleInput.ask("Date (yyyy-MM-dd): ")));
                case "3" -> complete();
                case "4" -> prescribe();
                case "5" -> cancel();
                case "6" -> earnings();
                case "7" -> changePassword();
                default  -> TableFormatter.error("Invalid option");
            }
        } catch (HospitalException e) {
            TableFormatter.error(e.getMessage());
        }
        return true;
    }

    private void schedule(LocalDate date) throws HospitalException {
        TableFormatter.subHeader("Schedule for " + date);
        List<Slot> slots = registry.getSlotService().getSlots(me(), date);
        List<Appointment> appts = registry.getAppointmentRepository()
                .findByDoctorAndDate(me().getId(), date);

        for (Slot slot : slots) {
            String detail = "";
            if (slot.isBooked()) {
                for (Appointment a : appts) {
                    if (a.getId().equals(slot.getAppointmentId())) {
                        detail = "  " + a.getId() + "  " + patientName(a.getPatientId()) + "  - " + a.getReason();
                    }
                }
            }
            System.out.printf("   %s-%s  %-8s%s%n", slot.getStart(), slot.getEnd(), slot.statusLabel(), detail);
        }
        System.out.printf("%nBooked %d of %d slots (%.1f%% occupancy)%n",
                slots.stream().filter(Slot::isBooked).count(), slots.size(),
                registry.getSlotService().occupancyRate(me(), date));
    }

    private void complete() throws HospitalException {
        List<Appointment> today = active(LocalDate.now());
        if (today.isEmpty()) {
            TableFormatter.info("No open appointments today.");
            return;
        }
        today.forEach(a -> System.out.println("  " + a + "  " + patientName(a.getPatientId())));
        String id = ConsoleInput.ask("\nAppointment id to complete: ").toUpperCase();
        registry.getBookingService().complete(id, me().getId());
        TableFormatter.success(id + " marked COMPLETED. You can now issue a prescription.");
    }

    private void prescribe() throws HospitalException {
        List<Appointment> completed = new ArrayList<>();
        for (Appointment a : registry.getAppointmentRepository().findByDoctor(me().getId())) {
            if (a.getStatus().name().equals("COMPLETED")
                    && registry.getPrescriptionService().forAppointment(a.getId()).isEmpty()) {
                completed.add(a);
            }
        }
        if (completed.isEmpty()) {
            TableFormatter.info("No completed appointments are waiting for a prescription.");
            return;
        }
        completed.forEach(a -> System.out.println("  " + a.getId() + "  " + a.getDate()
                + "  " + patientName(a.getPatientId())));

        String id = ConsoleInput.ask("\nAppointment id: ").toUpperCase();
        String diagnosis = ConsoleInput.ask("Diagnosis: ");
        List<String> medicines = new ArrayList<>();
        System.out.println("Enter medicines one per line (blank line to finish):");
        while (true) {
            String med = ConsoleInput.ask("  medicine: ");
            if (med.isBlank()) break;
            medicines.add(med);
        }
        String advice = ConsoleInput.askOrDefault("Advice [Review if symptoms persist]: ",
                "Review if symptoms persist");

        Prescription rx = registry.getPrescriptionService()
                .issue(id, me().getId(), diagnosis, medicines, advice);
        TableFormatter.success("Prescription " + rx.getId() + " issued with "
                + rx.getMedicines().size() + " item(s).");
    }

    private void cancel() throws HospitalException {
        List<Appointment> upcoming = active(null);
        if (upcoming.isEmpty()) {
            TableFormatter.info("Nothing to cancel.");
            return;
        }
        upcoming.forEach(a -> System.out.println("  " + a + "  " + patientName(a.getPatientId())));
        String id = ConsoleInput.ask("\nAppointment id to cancel: ").toUpperCase();
        registry.getBookingService().cancel(id, me().getId());
        TableFormatter.success("Cancelled. The slot is free again.");
    }

    private void earnings() throws HospitalException {
        List<Appointment> all = registry.getAppointmentRepository().findByDoctor(me().getId());
        double gross = 0, completedTotal = 0;
        int completedCount = 0, cancelledCount = 0;
        for (Appointment a : all) {
            gross += a.calculateBill();
            switch (a.getStatus()) {
                case COMPLETED -> { completedTotal += a.calculateBill(); completedCount++; }
                case CANCELLED, NO_SHOW -> cancelledCount++;
                default -> { }
            }
        }
        TableFormatter.subHeader("Earnings summary");
        System.out.printf("%-30s %d%n", "Total appointments", all.size());
        System.out.printf("%-30s %d%n", "Completed", completedCount);
        System.out.printf("%-30s %d%n", "Cancelled / no-show", cancelledCount);
        TableFormatter.money("Realised from completed", completedTotal);
        TableFormatter.money("Gross billable", gross);
        TableFormatter.money("Estimated tax component", gross * 0.05);
    }

    private void changePassword() throws HospitalException {
        String oldPass = ConsoleInput.ask("Current password: ");
        String newPass = ConsoleInput.ask("New password: ");
        registry.getAuthService().changePassword(oldPass, newPass);
        TableFormatter.success("Password updated.");
    }

    /** Open appointments, optionally restricted to one date. */
    private List<Appointment> active(LocalDate date) throws HospitalException {
        List<Appointment> out = new ArrayList<>();
        List<Appointment> source = date == null
                ? registry.getAppointmentRepository().findByDoctor(me().getId())
                : registry.getAppointmentRepository().findByDoctorAndDate(me().getId(), date);
        for (Appointment a : source) {
            if (a.occupiesSlot()) out.add(a);
        }
        return out;
    }

    private String patientName(String patientId) {
        try {
            return registry.getUserRepository().findPatientById(patientId)
                    .map(p -> p.getFullName()).orElse(patientId);
        } catch (HospitalException e) {
            return patientId;
        }
    }
}
