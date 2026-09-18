package com.hospital.model;

/**
 * Interface (Unit 2). Anything that can produce a payable amount.
 * Implemented by Appointment; kept separate so future entities such as
 * LabTest or Pharmacy bills can plug into ReportService unchanged.
 */
public interface Billable {

    /** Net payable amount in INR. */
    double calculateBill();

    /** Default method: GST component, overridable by implementors. */
    default double taxComponent() { return calculateBill() * 0.05; }
}
