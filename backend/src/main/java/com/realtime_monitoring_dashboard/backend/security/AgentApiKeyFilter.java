package com.realtime_monitoring_dashboard.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AgentApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Agent-Key";
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/devices/register",
            "/api/devices/ingest"
    );

    private final byte[] expectedKey;

    public AgentApiKeyFilter(String agentApiKey) {
        this.expectedKey = agentApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!PROTECTED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or missing agent API key\",\"path\":\"%s\"}"
                            .formatted(LocalDateTime.now(), request.getRequestURI()));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
