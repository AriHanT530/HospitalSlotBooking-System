package com.hospital.service;

import com.hospital.dao.AppointmentRepository;
import com.hospital.exception.DataAccessException;
import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.Slot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module 2a: turns a doctor's working window into a slot grid for one date and
 * overlays the appointments that already hold a slot.
 *
 * Slots are computed, never stored, so a change to a doctor's timings takes
 * effect immediately for all future dates.
 */
public class SlotService {

    private final AppointmentRepository appointments;

    public SlotService(AppointmentRepository appointments) { this.appointments = appointments; }

    /** Empty grid: pure function of the doctor's timings. */
    public List<Slot> generateGrid(Doctor doctor, LocalDate date) {
        List<Slot> slots = new ArrayList<>();
        LocalTime cursor = doctor.getWorkStart();
        int minutes = doctor.getSlotMinutes();
        while (!cursor.plusMinutes(minutes).isAfter(doctor.getWorkEnd())) {
            LocalTime end = cursor.plusMinutes(minutes);
            slots.add(new Slot(doctor.getId(), date, cursor, end));
            cursor = end;
        }
        return slots;
    }

    /** Grid with BOOKED markers applied. */
    public List<Slot> getSlots(Doctor doctor, LocalDate date) throws DataAccessException {
        List<Slot> grid = generateGrid(doctor, date);
        Map<LocalTime, Appointment> taken = new HashMap<>();
        for (Appointment a : appointments.findByDoctorAndDate(doctor.getId(), date)) {
            if (a.occupiesSlot()) taken.put(a.getStartTime(), a);
        }
        for (Slot slot : grid) {
            Appointment a = taken.get(slot.getStart());
            if (a != null) {
                slot.setBooked(true);
                slot.setAppointmentId(a.getId());
            }
        }
        return grid;
    }

    /** Only the slots a patient could actually pick right now. */
    public List<Slot> getAvailableSlots(Doctor doctor, LocalDate date) throws DataAccessException {
        List<Slot> free = new ArrayList<>();
        for (Slot slot : getSlots(doctor, date)) {
            if (slot.isBookable()) free.add(slot);
        }
        return free;
    }

    /** Next date within the horizon that still has at least one free slot. */
    public LocalDate nextAvailableDate(Doctor doctor, LocalDate from, int horizonDays)
            throws DataAccessException {
        for (int i = 0; i < horizonDays; i++) {
            LocalDate candidate = from.plusDays(i);
            if (!getAvailableSlots(doctor, candidate).isEmpty()) return candidate;
        }
        return null;
    }

    public double occupancyRate(Doctor doctor, LocalDate date) throws DataAccessException {
        List<Slot> slots = getSlots(doctor, date);
        if (slots.isEmpty()) return 0;
        long booked = slots.stream().filter(Slot::isBooked).count();
        return (booked * 100.0) / slots.size();
    }
}
