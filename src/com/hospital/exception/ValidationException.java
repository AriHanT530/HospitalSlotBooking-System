package com.hospital.exception;

/** Thrown by Validator when user input fails a business rule. */
public class ValidationException extends HospitalException {
    public ValidationException(String message) { super("VALIDATION", message); }
}
