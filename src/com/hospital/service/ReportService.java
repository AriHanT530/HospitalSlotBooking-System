package com.hospital.service;

import com.hospital.dao.AppointmentRepository;
import com.hospital.dao.UserRepository;
import com.hospital.exception.DataAccessException;
import com.hospital.exception.HospitalException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.model.Billable;
import com.hospital.model.Doctor;
import com.hospital.model.Specialization;
import com.hospital.util.AppLogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Module 3b: analytics over the appointment book.
 * Heavy use of the Collections Framework (Unit 4): Map, List, TreeMap for
 * ordered date keys, EnumMap for status counts, and Comparator-based sorting.
 * Export writes a CSV through a character stream.
 */
public class ReportService {

    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final AppLogger log = AppLogger.getInstance();

    public ReportService(AppointmentRepository appointments, UserRepository users) {
        this.appointments = appointments;
        this.users = users;
    }

    /** Revenue per doctor, highest first. Sums over Billable, not Appointment. */
    public Map<String, Double> revenueByDoctor() throws DataAccessException {
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, Double> raw = new java.util.HashMap<>();
        for (Appointment a : appointments.findAll()) {
            Billable billable = a;
            raw.merge(a.getDoctorId(), billable.calculateBill(), Double::sum);
        }
        raw.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> totals.put(e.getKey(), e.getValue()));
        return totals;
    }

    public Map<AppointmentStatus, Integer> statusBreakdown() throws DataAccessException {
        Map<AppointmentStatus, Integer> counts = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus s : AppointmentStatus.values()) counts.put(s, 0);
        for (Appointment a : appointments.findAll()) {
            counts.merge(a.getStatus(), 1, Integer::sum);
        }
        return counts;
    }

    /** Appointments per day, chronologically (TreeMap keeps keys sorted). */
    public Map<LocalDate, Integer> dailyLoad() throws DataAccessException {
        Map<LocalDate, Integer> load = new TreeMap<>();
        for (Appointment a : appointments.findAll()) {
            load.merge(a.getDate(), 1, Integer::sum);
        }
        return load;
    }

    public Map<Specialization, Integer> demandBySpecialization() throws DataAccessException {
        Map<Specialization, Integer> demand = new EnumMap<>(Specialization.class);
        Map<String, Specialization> doctorSpec = new java.util.HashMap<>();
        for (Doctor d : users.findDoctors()) doctorSpec.put(d.getId(), d.getSpecialization());
        for (Appointment a : appointments.findAll()) {
            Specialization s = doctorSpec.get(a.getDoctorId());
            if (s != null) demand.merge(s, 1, Integer::sum);
        }
        return demand;
    }

    public double totalRevenue() throws DataAccessException {
        double sum = 0;
        for (Appointment a : appointments.findAll()) sum += a.calculateBill();
        return sum;
    }

    public double cancellationRate() throws DataAccessException {
        List<Appointment> all = appointments.findAll();
        if (all.isEmpty()) return 0;
        long cancelled = all.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED
                          || a.getStatus() == AppointmentStatus.NO_SHOW)
                .count();
        return cancelled * 100.0 / all.size();
    }

    /** Busiest doctors by number of consultations actually delivered. */
    public List<String> topDoctors(int limit) throws DataAccessException {
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (Appointment a : appointments.findAll()) {
            if (a.getStatus() == AppointmentStatus.COMPLETED) counts.merge(a.getDoctorId(), 1, Integer::sum);
        }
        List<String> out = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> {
                    String name = doctorName(e.getKey());
                    out.add(String.format("%-8s %-24s %d completed", e.getKey(), name, e.getValue()));
                });
        return out;
    }

    private String doctorName(String doctorId) {
        try {
            return users.findDoctorById(doctorId).map(d -> "Dr. " + d.getFullName()).orElse("(unknown)");
        } catch (DataAccessException e) {
            return "(unknown)";
        }
    }

    /** Writes a full appointment dump to reports/ and returns the path. */
    public Path exportAppointmentsCsv() throws HospitalException {
        String stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path out = Paths.get("reports", "appointments_" + stamp + ".csv");
        try {
            Files.createDirectories(out.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                w.write("AppointmentId,Date,Start,End,DoctorId,DoctorName,PatientId,Status,Fee,Payable");
                w.newLine();
                for (Appointment a : appointments.findAll()) {
                    w.write(String.join(",",
                            a.getId(), a.getDate().toString(), a.getStartTime().toString(),
                            a.getEndTime().toString(), a.getDoctorId(),
                            "\"" + doctorName(a.getDoctorId()) + "\"", a.getPatientId(),
                            a.getStatus().name(), String.format("%.2f", a.getFee()),
                            String.format("%.2f", a.calculateBill())));
                    w.newLine();
                }
            }
            log.audit("EXPORT report " + out);
            return out;
        } catch (IOException e) {
            throw new com.hospital.exception.DataAccessException("Could not write report " + out, e);
        }
    }
}
