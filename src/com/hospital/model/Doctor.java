package com.hospital.model;

import java.time.LocalTime;

/**
 * A doctor exposes a working window that SlotService divides into fixed-length
 * consultation slots. The slot length defaults to the specialization value but
 * can be overridden per doctor.
 */
public class Doctor extends User {

    private Specialization specialization;
    private double consultationFee;
    private String roomNo;
    private LocalTime workStart;
    private LocalTime workEnd;
    private int slotMinutes;

    public Doctor(String id, String username, String passwordHash, String fullName, String phone,
                  Specialization specialization, double consultationFee, String roomNo,
                  LocalTime workStart, LocalTime workEnd, int slotMinutes) {
        super(id, username, passwordHash, fullName, phone);
        this.specialization = specialization;
        this.consultationFee = consultationFee;
        this.roomNo = roomNo;
        this.workStart = workStart;
        this.workEnd = workEnd;
        this.slotMinutes = slotMinutes > 0 ? slotMinutes : specialization.getDefaultSlotMinutes();
    }

    @Override
    public Role getRole() { return Role.DOCTOR; }

    @Override
    public String describe() {
        return String.format("%-8s Dr. %-20s %-18s Room %-5s %s-%s  Fee INR %.2f",
                getId(), getFullName(), specialization.getDisplayName(), roomNo,
                workStart, workEnd, consultationFee);
    }

    /** Total theoretical slots per working day. */
    public int slotsPerDay() {
        int minutes = (workEnd.toSecondOfDay() - workStart.toSecondOfDay()) / 60;
        return Math.max(0, minutes / slotMinutes);
    }

    public Specialization getSpecialization() { return specialization; }
    public void setSpecialization(Specialization specialization) { this.specialization = specialization; }
    public double getConsultationFee() { return consultationFee; }
    public void setConsultationFee(double consultationFee) { this.consultationFee = consultationFee; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public LocalTime getWorkStart() { return workStart; }
    public void setWorkStart(LocalTime workStart) { this.workStart = workStart; }
    public LocalTime getWorkEnd() { return workEnd; }
    public void setWorkEnd(LocalTime workEnd) { this.workEnd = workEnd; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
}
