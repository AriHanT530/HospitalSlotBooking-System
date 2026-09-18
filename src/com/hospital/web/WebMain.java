package com.hospital.web;

import com.hospital.service.DataSeeder;
import com.hospital.service.ServiceRegistry;
import com.hospital.util.AppLogger;

/**
 * Entry point for the browser front end. Boots the exact same service
 * layer as {@code com.hospital.Main} (the console app) - only the
 * presentation layer differs, everything else, including the booking
 * lock and validation rules, is shared code.
 */
public final class WebMain {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                System.err.println("Ignoring invalid port '" + args[0] + "', using 8080");
            }
        }

        AppLogger log = AppLogger.getInstance();
        log.info("Web application starting (Java " + System.getProperty("java.version") + ")");

        ServiceRegistry registry = new ServiceRegistry();
        DataSeeder.seedIfEmpty(registry.getAuthService());
        registry.startBackgroundJobs();

        Runtime.getRuntime().addShutdownHook(new Thread(registry::shutdown, "shutdown-hook"));

        new WebServer(registry).start(port);
    }
}
