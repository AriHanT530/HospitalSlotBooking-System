package com.hospital.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Wrapper around a BufferedReader on System.in (Unit 4: Reader streams).
 * A single shared reader avoids the classic Scanner buffering problems and
 * gives one place to handle EOF (Ctrl+D) gracefully.
 */
public final class ConsoleInput {

    private static final BufferedReader READER = new BufferedReader(new InputStreamReader(System.in));

    private ConsoleInput() { }

    public static String ask(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        try {
            String line = READER.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException e) {
            AppLogger.getInstance().error("Console read failed", e);
            return "";
        }
    }

    public static String askOrDefault(String prompt, String fallback) {
        String value = ask(prompt);
        return value.isBlank() ? fallback : value;
    }

    public static void pause() {
        ask("\nPress ENTER to continue...");
    }
}
