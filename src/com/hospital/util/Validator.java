package com.hospital.util;

import com.hospital.exception.ValidationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Single place for all input rules. Every public method either returns a
 * clean value or throws ValidationException - no nulls, no silent defaults.
 */
public final class Validator {

    private Validator() { }

    public static String requireText(String value, String field) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " cannot be empty");
        }
        return value.trim();
    }

    public static String requireName(String value) throws ValidationException {
        String v = requireText(value, "Name");
        if (!v.matches("[A-Za-z][A-Za-z .'-]{1,49}")) {
            throw new ValidationException("Name must be 2-50 letters (spaces, dot, apostrophe and hyphen allowed)");
        }
        return v;
    }

    public static String requireUsername(String value) throws ValidationException {
        String v = requireText(value, "Username").toLowerCase();
        if (!v.matches("[a-z0-9_.]{3,20}")) {
            throw new ValidationException("Username must be 3-20 chars: a-z, 0-9, underscore or dot");
        }
        return v;
    }

    public static String requirePassword(String value) throws ValidationException {
        if (value == null || value.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters long");
        }
        return value;
    }

    public static String requirePhone(String value) throws ValidationException {
        String v = requireText(value, "Phone").replaceAll("[\\s-]", "");
        if (!v.matches("[6-9][0-9]{9}")) {
            throw new ValidationException("Phone must be a valid 10-digit number starting with 6-9");
        }
        return v;
    }

    public static int requireAge(String value) throws ValidationException {
        try {
            int age = Integer.parseInt(requireText(value, "Age"));
            if (age < 0 || age > 120) throw new ValidationException("Age must be between 0 and 120");
            return age;
        } catch (NumberFormatException e) {
            throw new ValidationException("Age must be a whole number");
        }
    }

    public static double requireFee(String value) throws ValidationException {
        try {
            double fee = Double.parseDouble(requireText(value, "Fee"));
            if (fee < 0 || fee > 100000) throw new ValidationException("Fee must be between 0 and 100000");
            return fee;
        } catch (NumberFormatException e) {
            throw new ValidationException("Fee must be a number");
        }
    }

    public static String requireGender(String value) throws ValidationException {
        String v = requireText(value, "Gender").toUpperCase();
        if (!v.equals("M") && !v.equals("F") && !v.equals("O")) {
            throw new ValidationException("Gender must be M, F or O");
        }
        return v;
    }

    public static LocalDate requireDate(String value) throws ValidationException {
        try {
            return LocalDate.parse(requireText(value, "Date"));
        } catch (DateTimeParseException e) {
            throw new ValidationException("Date must be in yyyy-MM-dd format, e.g. 2026-09-20");
        }
    }

    public static LocalDate requireFutureDate(String value) throws ValidationException {
        LocalDate date = requireDate(value);
        if (date.isBefore(LocalDate.now())) {
            throw new ValidationException("Appointments cannot be booked for a past date");
        }
        if (date.isAfter(LocalDate.now().plusDays(60))) {
            throw new ValidationException("Appointments can only be booked up to 60 days ahead");
        }
        return date;
    }

    public static LocalTime requireTime(String value) throws ValidationException {
        try {
            return LocalTime.parse(requireText(value, "Time"));
        } catch (DateTimeParseException e) {
            throw new ValidationException("Time must be in HH:mm format, e.g. 10:30");
        }
    }

    public static int requireRange(String value, int min, int max, String field) throws ValidationException {
        try {
            int n = Integer.parseInt(requireText(value, field));
            if (n < min || n > max) throw new ValidationException(field + " must be between " + min + " and " + max);
            return n;
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a whole number");
        }
    }
}
