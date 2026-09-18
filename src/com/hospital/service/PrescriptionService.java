package com.hospital.service;

import com.hospital.dao.AppointmentRepository;
import com.hospital.dao.PrescriptionRepository;
import com.hospital.exception.EntityNotFoundException;
import com.hospital.exception.HospitalException;
import com.hospital.exception.ValidationException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.model.Prescription;
import com.hospital.util.AppLogger;
import com.hospital.util.IdGenerator;
import com.hospital.util.Validator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Module 3a: medical records attached to completed consultations. */
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final AppointmentRepository appointments;
    private final AppLogger log = AppLogger.getInstance();

    public PrescriptionService(PrescriptionRepository prescriptions, AppointmentRepository appointments) {
        this.prescriptions = prescriptions;
        this.appointments = appointments;
    }

    public Prescription issue(String appointmentId, String doctorId, String diagnosis,
                              List<String> medicines, String advice) throws HospitalException {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Appointment", appointmentId));
        if (!appointment.getDoctorId().equals(doctorId)) {
            throw new ValidationException("Appointment " + appointmentId + " is not yours");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ValidationException("Mark the appointment COMPLETED before issuing a prescription");
        }
        if (prescriptions.findByAppointment(appointmentId).isPresent()) {
            throw new ValidationException("A prescription already exists for " + appointmentId);
        }
        if (medicines == null || medicines.isEmpty()) {
            throw new ValidationException("At least one medicine or investigation must be listed");
        }

        Prescription prescription = new Prescription(
                IdGenerator.next(PrescriptionRepository.PREFIX), appointmentId,
                Validator.requireText(diagnosis, "Diagnosis"), medicines,
                advice == null || advice.isBlank() ? "Review if symptoms persist" : advice.trim(),
                LocalDateTime.now());
        prescriptions.save(prescription);
        log.audit("PRESCRIBE " + prescription.getId() + " for " + appointmentId);
        return prescription;
    }

    public Optional<Prescription> forAppointment(String appointmentId) throws HospitalException {
        return prescriptions.findByAppointment(appointmentId);
    }

    /** Full medical history of one patient, newest first. */
    public List<Prescription> historyOfPatient(String patientId) throws HospitalException {
        List<String> ids = appointments.findByPatient(patientId).stream()
                .map(Appointment::getId).toList();
        return prescriptions.findByAppointmentIds(ids).stream()
                .sorted(java.util.Comparator.comparing(Prescription::getIssuedAt).reversed())
                .toList();
    }
}
