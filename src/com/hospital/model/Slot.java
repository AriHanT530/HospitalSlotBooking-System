package com.hospital.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A slot is a derived (computed) entity - it is never persisted.
 * SlotService generates the grid for a doctor/date and marks the ones that
 * an active appointment already occupies.
 */
public class Slot {

    private final String doctorId;
    private final LocalDate date;
    private final LocalTime start;
    private final LocalTime end;
    private boolean booked;
    private String appointmentId;

    public Slot(String doctorId, LocalDate date, LocalTime start, LocalTime end) {
        this.doctorId = doctorId;
        this.date = date;
        this.start = start;
        this.end = end;
    }

    /** A slot in the past can never be booked, regardless of its booked flag. */
    public boolean isBookable() {
        return !booked && LocalDateTime.of(date, start).isAfter(LocalDateTime.now());
    }

    public String statusLabel() {
        if (booked) return "BOOKED";
        return isBookable() ? "FREE" : "ELAPSED";
    }

    public String getDoctorId() { return doctorId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
    public boolean isBooked() { return booked; }
    public void setBooked(boolean booked) { this.booked = booked; }
    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    @Override
    public String toString() {
        return String.format("%s - %s [%s]", start, end, statusLabel());
    }
}
