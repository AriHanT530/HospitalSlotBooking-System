package com.hospital.web;

import com.hospital.model.User;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store keyed by an opaque token carried in a cookie.
 * ConcurrentHashMap because requests from different browser tabs/users
 * arrive on different HttpServer worker threads simultaneously.
 */
public class SessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConcurrentHashMap<String, User> sessions = new ConcurrentHashMap<>();

    public String createSession(User user) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, user);
        return token;
    }

    public User get(String token) {
        return token == null ? null : sessions.get(token);
    }

    public void invalidate(String token) {
        if (token != null) sessions.remove(token);
    }
}
