package com.hospital.model;

/** Lifecycle of an appointment. Only BOOKED appointments hold a slot. */
public enum AppointmentStatus {
    BOOKED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /** A slot is considered occupied only while the appointment is active. */
    public boolean occupiesSlot() { return this == BOOKED; }
}
