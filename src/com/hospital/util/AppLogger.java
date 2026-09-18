package com.hospital.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe file logger built on character streams (Unit 4: Writer / I/O).
 * Implemented as a Singleton (Unit 2) so every thread appends to one handle
 * and log lines never interleave.
 */
public final class AppLogger {

    public enum Level { INFO, WARN, ERROR, AUDIT }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static AppLogger instance;

    private final Path logFile;
    private boolean echoToConsole = false;

    private AppLogger(Path logFile) { this.logFile = logFile; }

    /** Double-checked locking singleton accessor. */
    public static AppLogger getInstance() {
        if (instance == null) {
            synchronized (AppLogger.class) {
                if (instance == null) {
                    instance = new AppLogger(Paths.get("logs", "hospital.log"));
                    instance.ensureFile();
                }
            }
        }
        return instance;
    }

    private void ensureFile() {
        try {
            Path parent = logFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(logFile)) Files.createFile(logFile);
        } catch (IOException e) {
            System.err.println("Logger could not initialise: " + e.getMessage());
        }
    }

    public void setEchoToConsole(boolean echo) { this.echoToConsole = echo; }

    public void info(String msg)  { write(Level.INFO, msg); }
    public void warn(String msg)  { write(Level.WARN, msg); }
    public void error(String msg) { write(Level.ERROR, msg); }
    public void audit(String msg) { write(Level.AUDIT, msg); }

    public void error(String msg, Throwable t) {
        write(Level.ERROR, msg + " | cause=" + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    /** synchronized: many threads (UI + reminder daemon) log concurrently. */
    private synchronized void write(Level level, String message) {
        String line = String.format("%s [%-5s] [%s] %s",
                LocalDateTime.now().format(TS), level, Thread.currentThread().getName(), message);
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile.toFile(), true)))) {
            out.println(line);
        } catch (IOException e) {
            System.err.println("Log write failed: " + e.getMessage());
        }
        if (echoToConsole) System.out.println("  > " + line);
    }

    /** Returns the last n lines, used by the admin "view logs" screen. */
    public java.util.List<String> tail(int n) {
        try {
            java.util.List<String> all = Files.readAllLines(logFile);
            return all.subList(Math.max(0, all.size() - n), all.size());
        } catch (IOException e) {
            return java.util.List.of("(log unavailable: " + e.getMessage() + ")");
        }
    }
}
