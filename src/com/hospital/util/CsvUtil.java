package com.hospital.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal pipe-delimited record encoder. A pipe inside a value is escaped so
 * that a free-text field (for example a diagnosis) can never corrupt the file.
 */
public final class CsvUtil {

    public static final String DELIM = "|";
    private static final String ESCAPED_PIPE = "&#124;";
    private static final String ESCAPED_NEWLINE = "&#10;";

    private CsvUtil() { }

    public static String join(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(DELIM);
            sb.append(escape(fields[i] == null ? "" : fields[i].toString()));
        }
        return sb.toString();
    }

    public static String[] split(String line, int expectedColumns) {
        String[] raw = line.split("\\" + DELIM, -1);
        String[] out = new String[expectedColumns];
        for (int i = 0; i < expectedColumns; i++) {
            out[i] = i < raw.length ? unescape(raw[i]) : "";
        }
        return out;
    }

    public static String escape(String value) {
        return value.replace(DELIM, ESCAPED_PIPE).replace("\n", ESCAPED_NEWLINE);
    }

    public static String unescape(String value) {
        return value.replace(ESCAPED_PIPE, DELIM).replace(ESCAPED_NEWLINE, "\n");
    }

    public static List<String> splitList(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isBlank()) return out;
        for (String part : value.split(";;")) {
            if (!part.isBlank()) out.add(part.trim());
        }
        return out;
    }

    public static String joinList(List<String> values) {
        return values == null ? "" : String.join(";;", values);
    }
}
