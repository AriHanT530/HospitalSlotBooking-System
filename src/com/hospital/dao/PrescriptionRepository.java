package com.hospital.dao;

import com.hospital.exception.DataAccessException;
import com.hospital.model.Prescription;
import com.hospital.util.CsvUtil;
import com.hospital.util.IdGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistence for prescriptions, keyed by the appointment they belong to. */
public class PrescriptionRepository extends CsvRepository<Prescription> {

    public static final String PREFIX = "RX";
    private static final int COLS = 6;

    public PrescriptionRepository() {
        super("prescriptions.csv");
        reseedIdCounter();
    }

    @Override
    protected String header() { return "id|appointmentId|diagnosis|medicines|advice|issuedAt"; }

    @Override
    protected String idOf(Prescription p) { return p.getId(); }

    @Override
    protected String toCsv(Prescription p) {
        return CsvUtil.join(p.getId(), p.getAppointmentId(), p.getDiagnosis(),
                CsvUtil.joinList(p.getMedicines()), p.getAdvice(), p.getIssuedAt());
    }

    @Override
    protected Prescription fromCsv(String line) {
        String[] c = CsvUtil.split(line, COLS);
        return new Prescription(c[0], c[1], c[2], CsvUtil.splitList(c[3]), c[4],
                LocalDateTime.parse(c[5]));
    }

    private void reseedIdCounter() {
        try {
            for (Prescription p : findAll()) {
                IdGenerator.seed(PREFIX, IdGenerator.extractNumber(p.getId(), PREFIX));
            }
        } catch (DataAccessException e) {
            log.error("Could not reseed prescription ids", e);
        }
    }

    public Optional<Prescription> findByAppointment(String appointmentId) throws DataAccessException {
        for (Prescription p : findAll()) {
            if (p.getAppointmentId().equals(appointmentId)) return Optional.of(p);
        }
        return Optional.empty();
    }

    public List<Prescription> findByAppointmentIds(List<String> ids) throws DataAccessException {
        List<Prescription> out = new ArrayList<>();
        for (Prescription p : findAll()) {
            if (ids.contains(p.getAppointmentId())) out.add(p);
        }
        return out;
    }
}
