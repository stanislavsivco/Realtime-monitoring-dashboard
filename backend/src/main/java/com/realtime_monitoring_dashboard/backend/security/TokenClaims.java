package com.realtime_monitoring_dashboard.backend.security;

import java.time.Instant;

import org.springframework.security.core.userdetails.UserDetails;

public record TokenClaims(String username, Integer tokenVersion, Instant expiresAt) {

    public boolean matches(UserDetails user) {
        return user instanceof VersionedUser versioned
                && versioned.getUsername().equals(username)
                && Integer.valueOf(versioned.getTokenVersion()).equals(tokenVersion);
    }
}
