package com.hospital.service;

import com.hospital.dao.AppointmentRepository;
import com.hospital.dao.UserRepository;
import com.hospital.exception.EntityNotFoundException;
import com.hospital.exception.HospitalException;
import com.hospital.exception.SlotUnavailableException;
import com.hospital.exception.ValidationException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.model.Doctor;
import com.hospital.model.Patient;
import com.hospital.model.Slot;
import com.hospital.util.AppLogger;
import com.hospital.util.IdGenerator;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module 2b and the heart of the project: conflict-free slot booking.
 *
 * Unit 3 (synchronization) in a setting where it genuinely matters. Two
 * patients clicking the same 10:30 slot at the same instant must not both
 * succeed. The check-then-write sequence is therefore executed inside a lock
 * that is held per doctor, so bookings for different doctors still run in
 * parallel while bookings for the same doctor are serialised.
 */
public class BookingService {

    /** Cancellations inside this window are billed as a no-show. */
    public static final long LATE_CANCEL_HOURS = 2;
    /** A patient may not hold more than this many future appointments. */
    public static final int MAX_ACTIVE_PER_PATIENT = 5;

    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final SlotService slotService;
    private final AppLogger log = AppLogger.getInstance();

    /** One lock object per doctor id; created on first use. */
    private final Map<String, Object> doctorLocks = new ConcurrentHashMap<>();

    public BookingService(AppointmentRepository appointments, UserRepository users, SlotService slotService) {
        this.appointments = appointments;
        this.users = users;
        this.slotService = slotService;
    }

    private Object lockFor(String doctorId) {
        return doctorLocks.computeIfAbsent(doctorId, k -> new Object());
    }

    /**
     * Books one slot. Every validation that could reject the booking happens
     * inside the synchronized block, otherwise another thread could slip in
     * between the availability check and the write.
     */
    public Appointment book(String patientId, String doctorId, LocalDate date,
                            LocalTime startTime, String reason) throws HospitalException {

        Patient patient = users.findPatientById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Patient", patientId));
        Doctor doctor = users.findDoctorById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor", doctorId));

        if (!doctor.isActive()) {
            throw new ValidationException("Dr. " + doctor.getFullName() + " is not accepting appointments");
        }

        LocalTime endTime = startTime.plusMinutes(doctor.getSlotMinutes());

        synchronized (lockFor(doctorId)) {
            // 1. the requested time must be a real slot on this doctor's grid
            boolean onGrid = slotService.generateGrid(doctor, date).stream()
                    .anyMatch(s -> s.getStart().equals(startTime));
            if (!onGrid) {
                throw new SlotUnavailableException(startTime + " is not a valid slot for Dr. "
                        + doctor.getFullName() + " (working " + doctor.getWorkStart()
                        + "-" + doctor.getWorkEnd() + ", " + doctor.getSlotMinutes() + " min slots)");
            }

            // 2. it must still be in the future
            if (!LocalDateTime.of(date, startTime).isAfter(LocalDateTime.now())) {
                throw new SlotUnavailableException("That slot has already elapsed");
            }

            // 3. the doctor must be free
            for (Appointment existing : appointments.findByDoctorAndDate(doctorId, date)) {
                if (existing.occupiesSlot() && existing.overlaps(date, startTime, endTime)) {
                    throw new SlotUnavailableException("Slot " + startTime
                            + " is already booked with Dr. " + doctor.getFullName());
                }
            }

            // 4. the patient must not be double-booked elsewhere at that time
            for (Appointment mine : appointments.findByPatient(patientId)) {
                if (mine.occupiesSlot() && mine.overlaps(date, startTime, endTime)) {
                    throw new SlotUnavailableException("You already have appointment "
                            + mine.getId() + " at that time");
                }
            }

            // 5. fair-use cap
            if (countActive(patientId) >= MAX_ACTIVE_PER_PATIENT) {
                throw new ValidationException("You already hold " + MAX_ACTIVE_PER_PATIENT
                        + " upcoming appointments. Cancel one before booking another.");
            }

            Appointment appointment = new Appointment(
                    IdGenerator.next(AppointmentRepository.PREFIX),
                    patientId, doctorId, date, startTime, endTime,
                    AppointmentStatus.BOOKED, doctor.getConsultationFee(),
                    reason == null || reason.isBlank() ? "General consultation" : reason.trim(),
                    LocalDateTime.now());

            appointments.save(appointment);
            log.audit("BOOK " + appointment.getId() + " patient=" + patientId
                    + " doctor=" + doctorId + " at " + date + " " + startTime);
            return appointment;
        }
    }

    /** Books the earliest free slot on that date - convenience for the UI. */
    public Appointment bookEarliest(String patientId, String doctorId, LocalDate date, String reason)
            throws HospitalException {
        Doctor doctor = users.findDoctorById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor", doctorId));
        List<Slot> free = slotService.getAvailableSlots(doctor, date);
        if (free.isEmpty()) {
            throw new SlotUnavailableException("No free slots left for Dr. " + doctor.getFullName() + " on " + date);
        }
        return book(patientId, doctorId, date, free.get(0).getStart(), reason);
    }

    /**
     * Cancels an appointment. Late cancellations are recorded as NO_SHOW so
     * the 50% charge in Appointment.calculateBill() applies.
     */
    public Appointment cancel(String appointmentId, String requestedByUserId) throws HospitalException {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Appointment", appointmentId));

        if (!appointment.getPatientId().equals(requestedByUserId)
                && !appointment.getDoctorId().equals(requestedByUserId)
                && !requestedByUserId.startsWith("ADM")) {
            throw new ValidationException("You are not allowed to cancel appointment " + appointmentId);
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ValidationException("Appointment " + appointmentId + " is already "
                    + appointment.getStatus());
        }

        long hoursLeft = Duration.between(LocalDateTime.now(), appointment.startsAt()).toHours();
        boolean late = hoursLeft < LATE_CANCEL_HOURS && hoursLeft >= 0;
        appointment.setStatus(late ? AppointmentStatus.NO_SHOW : AppointmentStatus.CANCELLED);
        appointments.update(appointment);

        log.audit("CANCEL " + appointmentId + " by " + requestedByUserId
                + (late ? " (late - 50% charge)" : ""));
        return appointment;
    }

    /** Doctor marks the consultation done; the slot stays consumed. */
    public Appointment complete(String appointmentId, String doctorId) throws HospitalException {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Appointment", appointmentId));
        if (!appointment.getDoctorId().equals(doctorId)) {
            throw new ValidationException("Appointment " + appointmentId + " does not belong to you");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ValidationException("Only a BOOKED appointment can be completed");
        }
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointments.update(appointment);
        log.audit("COMPLETE " + appointmentId);
        return appointment;
    }

    /** Moves a booking to another slot: cancel-free reschedule in one step. */
    public Appointment reschedule(String appointmentId, LocalDate newDate, LocalTime newStart,
                                  String requestedByUserId) throws HospitalException {
        Appointment original = appointments.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Appointment", appointmentId));
        if (!original.getPatientId().equals(requestedByUserId) && !requestedByUserId.startsWith("ADM")) {
            throw new ValidationException("You are not allowed to reschedule this appointment");
        }
        if (original.getStatus() != AppointmentStatus.BOOKED) {
            throw new ValidationException("Only a BOOKED appointment can be rescheduled");
        }

        synchronized (lockFor(original.getDoctorId())) {
            // free the old slot first so the new booking may reuse it
            original.setStatus(AppointmentStatus.CANCELLED);
            appointments.update(original);
            try {
                Appointment moved = book(original.getPatientId(), original.getDoctorId(),
                        newDate, newStart, original.getReason());
                log.audit("RESCHEDULE " + appointmentId + " -> " + moved.getId());
                return moved;
            } catch (HospitalException failure) {
                // roll back so a failed move never loses the original booking
                original.setStatus(AppointmentStatus.BOOKED);
                appointments.update(original);
                log.warn("Reschedule of " + appointmentId + " rolled back: " + failure.getMessage());
                throw failure;
            }
        }
    }

    public long countActive(String patientId) throws HospitalException {
        return appointments.findByPatient(patientId).stream()
                .filter(a -> a.occupiesSlot() && a.startsAt().isAfter(LocalDateTime.now()))
                .count();
    }

    public List<Appointment> upcomingForPatient(String patientId) throws HospitalException {
        return appointments.findByPatient(patientId).stream()
                .filter(a -> a.startsAt().isAfter(LocalDateTime.now()) && a.occupiesSlot())
                .sorted(java.util.Comparator.comparing(Appointment::startsAt))
                .toList();
    }
}
