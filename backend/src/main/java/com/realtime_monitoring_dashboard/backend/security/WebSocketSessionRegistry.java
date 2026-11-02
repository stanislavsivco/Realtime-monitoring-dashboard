package com.realtime_monitoring_dashboard.backend.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class WebSocketSessionRegistry {

    private final Map<String, TokenClaims> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, TokenClaims claims) {
        sessions.put(sessionId, claims);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public Map<String, TokenClaims> snapshot() {
        return Map.copyOf(sessions);
    }
}
