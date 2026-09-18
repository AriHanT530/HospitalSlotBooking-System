package com.hospital;

import com.hospital.service.DataSeeder;
import com.hospital.service.ServiceRegistry;
import com.hospital.ui.ConsoleApp;
import com.hospital.util.AppLogger;

/**
 * Application entry point.
 *
 * Hospital Appointment and Slot Booking System
 * CSE2006 - Programming in Java, VITyarthi Build Your Own Project.
 *
 * Responsibilities kept deliberately small: build the object graph, seed the
 * demo data, start the background threads, hand control to the console UI and
 * guarantee a clean shutdown.
 */
public final class Main {

    public static void main(String[] args) {
        AppLogger log = AppLogger.getInstance();
        if (args.length > 0 && args[0].equalsIgnoreCase("--verbose")) {
            log.setEchoToConsole(true);
        }
        log.info("Application starting (Java " + System.getProperty("java.version") + ")");

        ServiceRegistry registry = new ServiceRegistry();
        DataSeeder.seedIfEmpty(registry.getAuthService());
        registry.startBackgroundJobs();

        // Runs even on Ctrl+C, so the log always closes cleanly.
        Runtime.getRuntime().addShutdownHook(new Thread(registry::shutdown, "shutdown-hook"));

        try {
            new ConsoleApp(registry).run();
        } catch (RuntimeException fatal) {
            log.error("Fatal error, application terminating", fatal);
            System.err.println("A fatal error occurred: " + fatal.getMessage());
        } finally {
            registry.shutdown();
        }
    }
}
