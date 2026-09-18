package com.hospital.util;

/** Small helpers so every screen prints with the same look. */
public final class TableFormatter {

    private TableFormatter() { }

    public static void header(String title) {
        System.out.println();
        System.out.println(line('='));
        System.out.println("  " + title.toUpperCase());
        System.out.println(line('='));
    }

    public static void subHeader(String title) {
        System.out.println("\n-- " + title + " " + "-".repeat(Math.max(0, 60 - title.length())));
    }

    public static String line(char c) {
        return String.valueOf(c).repeat(70);
    }

    public static void info(String message)    { System.out.println("[i] " + message); }
    public static void success(String message) { System.out.println("[OK] " + message); }
    public static void error(String message)   { System.out.println("[!] " + message); }
    public static void money(String label, double amount) {
        System.out.printf("%-30s INR %,10.2f%n", label, amount);
    }
}
