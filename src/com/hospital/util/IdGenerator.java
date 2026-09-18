package com.hospital.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates human-readable ids (PAT0001, DOC0003, APT0012).
 * Backed by AtomicInteger so concurrent booking threads cannot produce
 * duplicate identifiers.
 */
public final class IdGenerator {

    private static final ConcurrentHashMap<String, AtomicInteger> COUNTERS = new ConcurrentHashMap<>();

    private IdGenerator() { }

    public static String next(String prefix) {
        AtomicInteger counter = COUNTERS.computeIfAbsent(prefix, k -> new AtomicInteger(0));
        return String.format("%s%04d", prefix, counter.incrementAndGet());
    }

    /** Called at start-up so new ids continue after the highest stored id. */
    public static void seed(String prefix, int highest) {
        COUNTERS.computeIfAbsent(prefix, k -> new AtomicInteger(0))
                .updateAndGet(current -> Math.max(current, highest));
    }

    public static int extractNumber(String id, String prefix) {
        if (id == null || !id.startsWith(prefix)) return 0;
        try {
            return Integer.parseInt(id.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
