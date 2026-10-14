package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256";
    private static final String OTHER_SECRET = "another-secret-that-is-long-enough-hs256";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void generatedTokenContainsUsername() {
        String token = jwtService.generateToken("john", "VIEWER");

        assertThat(jwtService.extractUsername(token)).isEqualTo("john");
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String foreignToken = new JwtService(OTHER_SECRET, 60_000).generateToken("john", "ADMIN");

        assertThatThrownBy(() -> jwtService.extractUsername(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken("john", "VIEWER");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder()
                .subject("john")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtService.extractUsername(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        String unsigned = Jwts.builder().subject("john").compact();

        assertThatThrownBy(() -> jwtService.extractUsername(unsigned))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void constructorRejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtService("  ", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");
    }

    @Test
    void constructorRejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void constructorRejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtService(SECRET, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_EXPIRATION_MS");
    }
}
