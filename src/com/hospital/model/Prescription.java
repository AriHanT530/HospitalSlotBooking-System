package com.hospital.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Issued by a doctor against a completed appointment.
 * Holds a List of medicines (Unit 4: Collections / List interface).
 */
public class Prescription {

    private final String id;
    private final String appointmentId;
    private String diagnosis;
    private final List<String> medicines = new ArrayList<>();
    private String advice;
    private LocalDateTime issuedAt;

    public Prescription(String id, String appointmentId, String diagnosis,
                        List<String> medicines, String advice, LocalDateTime issuedAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.diagnosis = diagnosis;
        if (medicines != null) this.medicines.addAll(medicines);
        this.advice = advice;
        this.issuedAt = issuedAt;
    }

    public void addMedicine(String medicine) {
        if (medicine != null && !medicine.isBlank()) medicines.add(medicine.trim());
    }

    public String getId() { return id; }
    public String getAppointmentId() { return appointmentId; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    /** Defensive copy - callers cannot mutate internal state. */
    public List<String> getMedicines() { return Collections.unmodifiableList(medicines); }
    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    @Override
    public String toString() {
        return String.format("%s | %s | %s | %s", id, appointmentId, diagnosis, String.join(", ", medicines));
    }
}
