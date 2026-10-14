package com.realtime_monitoring_dashboard.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AgentApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Agent-Key";
    static final int MIN_KEY_BYTES = 32;

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/devices/register",
            "/api/devices/ingest"
    );

    private final byte[] expectedKey;
    private final SecurityErrorWriter errorWriter;

    public AgentApiKeyFilter(String agentApiKey, SecurityErrorWriter errorWriter) {
        if (agentApiKey == null || agentApiKey.isBlank()) {
            throw new IllegalStateException("AGENT_API_KEY must be set");
        }

        byte[] keyBytes = agentApiKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("AGENT_API_KEY must be at least " + MIN_KEY_BYTES + " bytes long");
        }

        this.expectedKey = keyBytes;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!PROTECTED_PATHS.contains(applicationPath(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Invalid or missing agent API key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
