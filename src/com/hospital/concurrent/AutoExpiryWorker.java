package com.hospital.concurrent;

import com.hospital.dao.AppointmentRepository;
import com.hospital.exception.DataAccessException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.util.AppLogger;

import java.time.LocalDateTime;

/**
 * Unit 3: thread creation by IMPLEMENTING Runnable - the second of the two
 * creation styles required by the syllabus, shown side by side with
 * ReminderDaemon so the difference is visible in one project.
 *
 * Housekeeping job: an appointment whose slot has passed while still BOOKED
 * is closed off as NO_SHOW, which releases it from "active" counts and keeps
 * the reports honest.
 */
public class AutoExpiryWorker implements Runnable {

    private final AppointmentRepository appointments;
    private final AppLogger log = AppLogger.getInstance();
    private final long intervalMillis;
    private volatile boolean running = true;

    public AutoExpiryWorker(AppointmentRepository appointments, long intervalSeconds) {
        this.appointments = appointments;
        this.intervalMillis = intervalSeconds * 1000L;
    }

    @Override
    public void run() {
        while (running) {
            try {
                int expired = expireOnce();
                if (expired > 0) log.info("Auto-expiry closed " + expired + " stale appointment(s)");
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (DataAccessException e) {
                log.error("Auto-expiry pass failed", e);
            }
        }
    }

    /**
     * synchronized on the repository: the console thread may be updating the
     * very same appointment while this pass runs.
     */
    public int expireOnce() throws DataAccessException {
        int changed = 0;
        LocalDateTime now = LocalDateTime.now();
        synchronized (appointments) {
            for (Appointment a : appointments.findAll()) {
                if (a.getStatus() == AppointmentStatus.BOOKED && a.endsAt().isBefore(now)) {
                    a.setStatus(AppointmentStatus.NO_SHOW);
                    appointments.update(a);
                    log.warn("AUTO_EXPIRE " + a.getId() + " marked NO_SHOW");
                    changed++;
                }
            }
        }
        return changed;
    }

    public void shutdown() { running = false; }
}
