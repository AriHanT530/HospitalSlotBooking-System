package com.hospital.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Core transactional entity. Implements Billable, so ReportService can total
 * revenue over a heterogeneous list of billable items (polymorphism).
 */
public class Appointment implements Billable {

    private final String id;
    private final String patientId;
    private final String doctorId;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private AppointmentStatus status;
    private double fee;
    private String reason;
    private LocalDateTime createdAt;

    public Appointment(String id, String patientId, String doctorId, LocalDate date,
                       LocalTime startTime, LocalTime endTime, AppointmentStatus status,
                       double fee, String reason, LocalDateTime createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.fee = fee;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    /**
     * Cancellations inside 2 hours of the slot forfeit 50% of the fee;
     * cancelled-in-time and no-show cases are handled separately.
     */
    @Override
    public double calculateBill() {
        switch (status) {
            case COMPLETED:
                return fee;
            case NO_SHOW:
                return fee * 0.5;
            case CANCELLED:
                return 0.0;
            case BOOKED:
            default:
                return fee;
        }
    }

    public boolean occupiesSlot() { return status.occupiesSlot(); }

    public LocalDateTime startsAt() { return LocalDateTime.of(date, startTime); }

    public LocalDateTime endsAt() { return LocalDateTime.of(date, endTime); }

    /** True when the appointment clashes with the given window on the same day. */
    public boolean overlaps(LocalDate otherDate, LocalTime otherStart, LocalTime otherEnd) {
        if (!date.equals(otherDate)) return false;
        return otherStart.isBefore(endTime) && startTime.isBefore(otherEnd);
    }

    public String getId() { return id; }
    public String getPatientId() { return patientId; }
    public String getDoctorId() { return doctorId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public double getFee() { return fee; }
    public void setFee(double fee) { this.fee = fee; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Appointment)) return false;
        return id.equals(((Appointment) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return String.format("%-8s %s %s-%s  doctor=%-8s patient=%-8s %-9s INR %.2f",
                id, date, startTime, endTime, doctorId, patientId, status, calculateBill());
    }
}
