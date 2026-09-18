package com.hospital.dao;

import com.hospital.exception.DataAccessException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.util.AppLogger;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unit 5: the same Repository contract implemented over JDBC.
 *
 * Demonstrates DriverManager connections, PreparedStatement parameter
 * binding (which also prevents SQL injection), ResultSet iteration and
 * try-with-resources cleanup. Swap this in for AppointmentRepository in
 * ServiceRegistry and the rest of the application is unchanged - that is the
 * point of programming against the interface.
 */
public class JdbcAppointmentRepository implements Repository<Appointment> {

    private static final AppLogger LOG = AppLogger.getInstance();

    private static final String INSERT =
            "INSERT INTO appointments (id, patient_id, doctor_id, appt_date, start_time, end_time," +
            " status, fee, reason, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE =
            "UPDATE appointments SET patient_id=?, doctor_id=?, appt_date=?, start_time=?, end_time=?," +
            " status=?, fee=?, reason=? WHERE id=?";
    private static final String SELECT_ALL =
            "SELECT * FROM appointments ORDER BY appt_date DESC, start_time";
    private static final String SELECT_BY_ID = "SELECT * FROM appointments WHERE id=?";
    private static final String DELETE = "DELETE FROM appointments WHERE id=?";
    private static final String SELECT_DOCTOR_DATE =
            "SELECT * FROM appointments WHERE doctor_id=? AND appt_date=? ORDER BY start_time";

    @Override
    public Appointment save(Appointment a) throws DataAccessException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT)) {
            bindCommon(ps, a);
            ps.setString(1, a.getId());
            ps.setTimestamp(10, Timestamp.valueOf(a.getCreatedAt()));
            ps.executeUpdate();
            LOG.info("JDBC insert appointment " + a.getId());
            return a;
        } catch (SQLException e) {
            throw new DataAccessException("Insert failed for appointment " + a.getId(), e);
        }
    }

    /** Columns 2..9 are shared between INSERT and UPDATE. */
    private void bindCommon(PreparedStatement ps, Appointment a) throws SQLException {
        ps.setString(2, a.getPatientId());
        ps.setString(3, a.getDoctorId());
        ps.setDate(4, Date.valueOf(a.getDate()));
        ps.setTime(5, Time.valueOf(a.getStartTime()));
        ps.setTime(6, Time.valueOf(a.getEndTime()));
        ps.setString(7, a.getStatus().name());
        ps.setDouble(8, a.getFee());
        ps.setString(9, a.getReason());
    }

    @Override
    public void update(Appointment a) throws DataAccessException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, a.getPatientId());
            ps.setString(2, a.getDoctorId());
            ps.setDate(3, Date.valueOf(a.getDate()));
            ps.setTime(4, Time.valueOf(a.getStartTime()));
            ps.setTime(5, Time.valueOf(a.getEndTime()));
            ps.setString(6, a.getStatus().name());
            ps.setDouble(7, a.getFee());
            ps.setString(8, a.getReason());
            ps.setString(9, a.getId());
            if (ps.executeUpdate() == 0) {
                throw new DataAccessException("No row updated for id " + a.getId(), null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Update failed for appointment " + a.getId(), e);
        }
    }

    @Override
    public boolean deleteById(String id) throws DataAccessException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Delete failed for appointment " + id, e);
        }
    }

    @Override
    public Optional<Appointment> findById(String id) throws DataAccessException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_BY_ID)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lookup failed for appointment " + id, e);
        }
    }

    @Override
    public List<Appointment> findAll() throws DataAccessException {
        List<Appointment> out = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Select all appointments failed", e);
        }
    }

    public List<Appointment> findByDoctorAndDate(String doctorId, java.time.LocalDate date)
            throws DataAccessException {
        List<Appointment> out = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_DOCTOR_DATE)) {
            ps.setString(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Doctor/date query failed", e);
        }
    }

    /** ResultSet row to domain object. */
    private Appointment map(ResultSet rs) throws SQLException {
        return new Appointment(
                rs.getString("id"),
                rs.getString("patient_id"),
                rs.getString("doctor_id"),
                rs.getDate("appt_date").toLocalDate(),
                rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime(),
                AppointmentStatus.valueOf(rs.getString("status")),
                rs.getDouble("fee"),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
