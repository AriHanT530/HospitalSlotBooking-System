package com.hospital.dao;

import com.hospital.exception.DataAccessException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.util.CsvUtil;
import com.hospital.util.IdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Persistence for appointments plus the queries the booking rules need. */
public class AppointmentRepository extends CsvRepository<Appointment> {

    public static final String PREFIX = "APT";
    private static final int COLS = 10;

    public AppointmentRepository() {
        super("appointments.csv");
        reseedIdCounter();
    }

    @Override
    protected String header() {
        return "id|patientId|doctorId|date|startTime|endTime|status|fee|reason|createdAt";
    }

    @Override
    protected String idOf(Appointment a) { return a.getId(); }

    @Override
    protected String toCsv(Appointment a) {
        return CsvUtil.join(a.getId(), a.getPatientId(), a.getDoctorId(), a.getDate(),
                a.getStartTime(), a.getEndTime(), a.getStatus(), a.getFee(),
                a.getReason(), a.getCreatedAt());
    }

    @Override
    protected Appointment fromCsv(String line) {
        String[] c = CsvUtil.split(line, COLS);
        return new Appointment(c[0], c[1], c[2],
                LocalDate.parse(c[3]), LocalTime.parse(c[4]), LocalTime.parse(c[5]),
                AppointmentStatus.valueOf(c[6]),
                Double.parseDouble(c[7].isBlank() ? "0" : c[7]),
                c[8], LocalDateTime.parse(c[9]));
    }

    private void reseedIdCounter() {
        try {
            for (Appointment a : findAll()) {
                IdGenerator.seed(PREFIX, IdGenerator.extractNumber(a.getId(), PREFIX));
            }
        } catch (DataAccessException e) {
            log.error("Could not reseed appointment ids", e);
        }
    }

    public List<Appointment> findByDoctorAndDate(String doctorId, LocalDate date) throws DataAccessException {
        List<Appointment> out = new ArrayList<>();
        for (Appointment a : findAll()) {
            if (a.getDoctorId().equals(doctorId) && a.getDate().equals(date)) out.add(a);
        }
        out.sort(Comparator.comparing(Appointment::getStartTime));
        return out;
    }

    public List<Appointment> findByDoctor(String doctorId) throws DataAccessException {
        List<Appointment> out = new ArrayList<>();
        for (Appointment a : findAll()) {
            if (a.getDoctorId().equals(doctorId)) out.add(a);
        }
        out.sort(Comparator.comparing(Appointment::startsAt).reversed());
        return out;
    }

    public List<Appointment> findByPatient(String patientId) throws DataAccessException {
        List<Appointment> out = new ArrayList<>();
        for (Appointment a : findAll()) {
            if (a.getPatientId().equals(patientId)) out.add(a);
        }
        out.sort(Comparator.comparing(Appointment::startsAt).reversed());
        return out;
    }

    public List<Appointment> findByDate(LocalDate date) throws DataAccessException {
        List<Appointment> out = new ArrayList<>();
        for (Appointment a : findAll()) {
            if (a.getDate().equals(date)) out.add(a);
        }
        out.sort(Comparator.comparing(Appointment::getStartTime));
        return out;
    }

    /** Used by the daemon: everything still BOOKED on the given day. */
    public List<Appointment> findActiveOn(LocalDate date) throws DataAccessException {
        List<Appointment> out = new ArrayList<>();
        for (Appointment a : findByDate(date)) {
            if (a.occupiesSlot()) out.add(a);
        }
        return out;
    }
}
