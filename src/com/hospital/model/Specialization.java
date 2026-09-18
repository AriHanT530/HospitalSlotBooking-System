package com.hospital.model;

/**
 * Enum with a constructor and fields (Unit 2: enum class, enum constructor).
 * Consultation duration differs per specialization, which directly affects
 * how many slots a doctor exposes in a working day.
 */
public enum Specialization {
    GENERAL("General Medicine", 15),
    CARDIOLOGY("Cardiology", 30),
    ORTHOPAEDICS("Orthopaedics", 30),
    PAEDIATRICS("Paediatrics", 20),
    DERMATOLOGY("Dermatology", 20),
    NEUROLOGY("Neurology", 45);

    private final String displayName;
    private final int defaultSlotMinutes;

    Specialization(String displayName, int defaultSlotMinutes) {
        this.displayName = displayName;
        this.defaultSlotMinutes = defaultSlotMinutes;
    }

    public String getDisplayName() { return displayName; }

    public int getDefaultSlotMinutes() { return defaultSlotMinutes; }

    /** Safe lookup that never throws IllegalArgumentException. */
    public static Specialization fromString(String value) {
        if (value == null) return GENERAL;
        for (Specialization s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) return s;
        }
        return GENERAL;
    }
}
