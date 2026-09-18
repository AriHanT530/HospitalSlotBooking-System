package com.hospital.service;

import com.hospital.concurrent.AutoExpiryWorker;
import com.hospital.concurrent.ReminderDaemon;
import com.hospital.dao.AppointmentRepository;
import com.hospital.dao.DatabaseManager;
import com.hospital.dao.PrescriptionRepository;
import com.hospital.dao.UserRepository;
import com.hospital.util.AppLogger;

/**
 * Manual dependency-injection container (composition root).
 * One place that knows how the objects are wired, which keeps every other
 * class free of "new" calls for its collaborators and makes the whole graph
 * easy to rebuild inside tests.
 */
public class ServiceRegistry {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;

    private final AuthService authService;
    private final SlotService slotService;
    private final BookingService bookingService;
    private final PrescriptionService prescriptionService;
    private final ReportService reportService;

    private ReminderDaemon reminderDaemon;
    private AutoExpiryWorker expiryWorker;
    private Thread expiryThread;

    public ServiceRegistry() {
        AppLogger log = AppLogger.getInstance();

        this.userRepository = new UserRepository();
        this.appointmentRepository = new AppointmentRepository();
        this.prescriptionRepository = new PrescriptionRepository();

        // Unit 5: if config/db.properties enables JDBC and a driver is present,
        // the JDBC repository could be substituted here without touching any
        // service class, because both sides implement Repository<Appointment>.
        if (DatabaseManager.isAvailable()) {
            log.info("JDBC storage detected and ready (CSV remains the active store by default)");
        }

        this.authService = new AuthService(userRepository);
        this.slotService = new SlotService(appointmentRepository);
        this.bookingService = new BookingService(appointmentRepository, userRepository, slotService);
        this.prescriptionService = new PrescriptionService(prescriptionRepository, appointmentRepository);
        this.reportService = new ReportService(appointmentRepository, userRepository);
    }

    /** Starts the two background workers. */
    public void startBackgroundJobs() {
        reminderDaemon = new ReminderDaemon(appointmentRepository, userRepository, 60, 24);
        reminderDaemon.start();

        expiryWorker = new AutoExpiryWorker(appointmentRepository, 120);
        expiryThread = new Thread(expiryWorker, "auto-expiry");
        expiryThread.setDaemon(true);
        expiryThread.start();
    }

    /** Graceful shutdown so the log always records a clean exit. */
    public void shutdown() {
        if (reminderDaemon != null) reminderDaemon.shutdown();
        if (expiryWorker != null) expiryWorker.shutdown();
        if (expiryThread != null) expiryThread.interrupt();
        AppLogger.getInstance().info("Application shutdown complete");
    }

    public UserRepository getUserRepository() { return userRepository; }
    public AppointmentRepository getAppointmentRepository() { return appointmentRepository; }
    public PrescriptionRepository getPrescriptionRepository() { return prescriptionRepository; }
    public AuthService getAuthService() { return authService; }
    public SlotService getSlotService() { return slotService; }
    public BookingService getBookingService() { return bookingService; }
    public PrescriptionService getPrescriptionService() { return prescriptionService; }
    public ReportService getReportService() { return reportService; }
    public ReminderDaemon getReminderDaemon() { return reminderDaemon; }
}
