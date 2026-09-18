package com.hospital.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small helpers for the parts HttpExchange leaves as raw plumbing. */
public final class WebUtil {

    private WebUtil() { }

    public static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(decode(key), decode(value));
        }
        return out;
    }

    public static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        return parseQuery(readBody(exchange));
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    public static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    public static String cookie(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    public static void setCookie(HttpExchange exchange, String name, String value) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=" + value + "; Path=/; HttpOnly; SameSite=Lax");
    }

    public static void clearCookie(HttpExchange exchange, String name) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=; Path=/; Max-Age=0");
    }

    public static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** Sends a file (used for the exported CSV report) as a download. */
    public static void sendDownload(HttpExchange exchange, byte[] bytes, String filename, String contentType)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** Redirect with an optional flash message carried in the query string. */
    public static void redirect(HttpExchange exchange, String path, String message, String type) throws IOException {
        String location = path;
        if (message != null && !message.isBlank()) {
            String sep = path.contains("?") ? "&" : "?";
            location = path + sep + "msg=" + encode(message) + "&mt=" + encode(type == null ? "ok" : type);
        }
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
