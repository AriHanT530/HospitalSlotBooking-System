package com.hospital.concurrent;

import com.hospital.dao.AppointmentRepository;
import com.hospital.dao.UserRepository;
import com.hospital.exception.DataAccessException;
import com.hospital.model.Appointment;
import com.hospital.util.AppLogger;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unit 3: thread creation by EXTENDING Thread.
 *
 * Background worker that wakes periodically, finds appointments starting
 * inside the reminder window and writes a reminder line to the log. It runs
 * as a daemon so the JVM can still exit when the user quits the menu.
 *
 * Thread life cycle on display: NEW (constructor) -> RUNNABLE (start) ->
 * TIMED_WAITING (sleep) -> RUNNABLE -> TERMINATED (shutdown flag + interrupt).
 */
public class ReminderDaemon extends Thread {

    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final AppLogger log = AppLogger.getInstance();
    private final long intervalMillis;
    private final long reminderWindowHours;

    /** volatile: the flag is written by the main thread and read by this one. */
    private volatile boolean running = true;
    private int cyclesRun = 0;

    public ReminderDaemon(AppointmentRepository appointments, UserRepository users,
                          long intervalSeconds, long reminderWindowHours) {
        super("reminder-daemon");
        this.appointments = appointments;
        this.users = users;
        this.intervalMillis = intervalSeconds * 1000L;
        this.reminderWindowHours = reminderWindowHours;
        setDaemon(true);
        setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run() {
        log.info("Reminder daemon started (every " + (intervalMillis / 1000) + "s, window "
                + reminderWindowHours + "h)");
        while (running) {
            try {
                int sent = scanOnce();
                cyclesRun++;
                if (sent > 0) log.info("Reminder cycle " + cyclesRun + ": " + sent + " reminder(s) queued");
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                // Restore the flag and leave the loop - the documented way to
                // handle interruption rather than swallowing it.
                Thread.currentThread().interrupt();
                break;
            } catch (DataAccessException e) {
                log.error("Reminder cycle failed", e);
            }
        }
        log.info("Reminder daemon stopped after " + cyclesRun + " cycle(s)");
    }

    /** Package-visible so the test suite can drive one cycle deterministically. */
    public int scanOnce() throws DataAccessException {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (LocalDate day : List.of(LocalDate.now(), LocalDate.now().plusDays(1))) {
            for (Appointment a : appointments.findActiveOn(day)) {
                long hours = Duration.between(now, a.startsAt()).toHours();
                if (hours >= 0 && hours <= reminderWindowHours) {
                    log.info("REMINDER " + a.getId() + " -> patient " + patientName(a.getPatientId())
                            + " at " + a.getDate() + " " + a.getStartTime()
                            + " (in " + hours + "h)");
                    count++;
                }
            }
        }
        return count;
    }

    private String patientName(String patientId) {
        try {
            return users.findPatientById(patientId).map(p -> p.getFullName()).orElse(patientId);
        } catch (DataAccessException e) {
            return patientId;
        }
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    public int getCyclesRun() { return cyclesRun; }
}
