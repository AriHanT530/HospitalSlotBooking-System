package com.hospital.dao;

import com.hospital.exception.DataAccessException;
import com.hospital.model.Admin;
import com.hospital.model.Doctor;
import com.hospital.model.Patient;
import com.hospital.model.Role;
import com.hospital.model.Specialization;
import com.hospital.model.User;
import com.hospital.util.CsvUtil;
import com.hospital.util.IdGenerator;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores every user type in one file and rebuilds the correct subclass on
 * read - a hand-written single-table inheritance mapping. The role column
 * decides which constructor runs (runtime polymorphism on the way out).
 */
public class UserRepository extends CsvRepository<User> {

    private static final int COLS = 14;

    public UserRepository() {
        super("users.csv");
        reseedIdCounters();
    }

    @Override
    protected String header() {
        return "id|role|username|passwordHash|fullName|phone|active|f1|f2|f3|f4|f5|f6|f7";
    }

    @Override
    protected String idOf(User user) { return user.getId(); }

    @Override
    protected String toCsv(User u) {
        if (u instanceof Patient) {
            Patient p = (Patient) u;
            return CsvUtil.join(p.getId(), Role.PATIENT, p.getUsername(), p.getPasswordHash(),
                    p.getFullName(), p.getPhone(), p.isActive(),
                    p.getAge(), p.getGender(), p.getBloodGroup(), "", "", "", "");
        }
        if (u instanceof Doctor) {
            Doctor d = (Doctor) u;
            return CsvUtil.join(d.getId(), Role.DOCTOR, d.getUsername(), d.getPasswordHash(),
                    d.getFullName(), d.getPhone(), d.isActive(),
                    d.getSpecialization(), d.getConsultationFee(), d.getRoomNo(),
                    d.getWorkStart(), d.getWorkEnd(), d.getSlotMinutes(), "");
        }
        Admin a = (Admin) u;
        return CsvUtil.join(a.getId(), Role.ADMIN, a.getUsername(), a.getPasswordHash(),
                a.getFullName(), a.getPhone(), a.isActive(),
                a.getDesignation(), "", "", "", "", "", "");
    }

    @Override
    protected User fromCsv(String line) {
        String[] c = CsvUtil.split(line, COLS);
        Role role = Role.valueOf(c[1]);
        boolean active = Boolean.parseBoolean(c[6]);
        User user;
        switch (role) {
            case PATIENT:
                user = new Patient(c[0], c[2], c[3], c[4], c[5],
                        Integer.parseInt(c[7].isBlank() ? "0" : c[7]), c[8], c[9]);
                break;
            case DOCTOR:
                user = new Doctor(c[0], c[2], c[3], c[4], c[5],
                        Specialization.fromString(c[7]),
                        Double.parseDouble(c[8].isBlank() ? "0" : c[8]), c[9],
                        LocalTime.parse(c[10]), LocalTime.parse(c[11]),
                        Integer.parseInt(c[12].isBlank() ? "30" : c[12]));
                break;
            case ADMIN:
            default:
                user = new Admin(c[0], c[2], c[3], c[4], c[5], c[7]);
                break;
        }
        user.setActive(active);
        return user;
    }

    /** Keeps generated ids unique across restarts. */
    private void reseedIdCounters() {
        try {
            for (User u : findAll()) {
                String prefix = prefixFor(u.getRole());
                IdGenerator.seed(prefix, IdGenerator.extractNumber(u.getId(), prefix));
            }
        } catch (DataAccessException e) {
            log.error("Could not reseed user id counters", e);
        }
    }

    public static String prefixFor(Role role) {
        switch (role) {
            case DOCTOR:  return "DOC";
            case PATIENT: return "PAT";
            default:      return "ADM";
        }
    }

    public Optional<User> findByUsername(String username) throws DataAccessException {
        if (username == null) return Optional.empty();
        for (User u : findAll()) {
            if (u.getUsername().equalsIgnoreCase(username.trim())) return Optional.of(u);
        }
        return Optional.empty();
    }

    public boolean usernameExists(String username) throws DataAccessException {
        return findByUsername(username).isPresent();
    }

    /** Generic filter by concrete type - avoids three near-identical methods. */
    public <T extends User> List<T> findByType(Class<T> type) throws DataAccessException {
        List<T> out = new ArrayList<>();
        for (User u : findAll()) {
            if (type.isInstance(u)) out.add(type.cast(u));
        }
        return out;
    }

    public List<Doctor> findDoctors() throws DataAccessException { return findByType(Doctor.class); }

    public List<Patient> findPatients() throws DataAccessException { return findByType(Patient.class); }

    public Optional<Doctor> findDoctorById(String id) throws DataAccessException {
        return findById(id).filter(u -> u instanceof Doctor).map(u -> (Doctor) u);
    }

    public Optional<Patient> findPatientById(String id) throws DataAccessException {
        return findById(id).filter(u -> u instanceof Patient).map(u -> (Patient) u);
    }
}
