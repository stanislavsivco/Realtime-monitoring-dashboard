package com.realtime_monitoring_dashboard.backend.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";
    static final String REGISTER_PATH = "/api/auth/register";

    private final Map<String, RateLimiter> limiters;
    private final SecurityErrorWriter errorWriter;

    public AuthRateLimitFilter(RateLimiter loginLimiter, RateLimiter registerLimiter, SecurityErrorWriter errorWriter) {
        this.limiters = Map.of(LOGIN_PATH, loginLimiter, REGISTER_PATH, registerLimiter);
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        RateLimiter limiter = limiters.get(AgentApiKeyFilter.applicationPath(request));
        if (limiter == null || !HttpMethod.POST.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = limiter.tryAcquire(request.getRemoteAddr());
        if (retryAfterSeconds > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts, try again in " + retryAfterSeconds + " seconds");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
