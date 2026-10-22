package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class AuthRateLimitFilterTest {

    private final SecurityErrorWriter errorWriter = new SecurityErrorWriter(JsonMapper.builder().build());
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new RateLimiter(2, Duration.ofMinutes(1)),
            new RateLimiter(1, Duration.ofHours(1)),
            errorWriter);

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }

    private MockHttpServletResponse send(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletResponse send(String method, String path, String ip) throws Exception {
        return send(request(method, path, ip), new MockFilterChain());
    }

    @Test
    void loginWithinLimitPassesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = send(request("POST", "/api/auth/login", "1.1.1.1"), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void loginOverLimitReturns429WithRetryAfterAndJsonBody() throws Exception {
        send("POST", "/api/auth/login", "1.1.1.1");
        send("POST", "/api/auth/login", "1.1.1.1");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = send(request("POST", "/api/auth/login", "1.1.1.1"), chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("Too many attempts")
                .contains("\"path\":\"/api/auth/login\"");
    }

    @Test
    void registerHasItsOwnStricterLimit() throws Exception {
        send("POST", "/api/auth/login", "1.1.1.1");
        send("POST", "/api/auth/login", "1.1.1.1");

        assertThat(send("POST", "/api/auth/register", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/auth/register", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void trailingSlashDoesNotBypassTheLimit() throws Exception {
        send("POST", "/api/auth/login", "1.1.1.1");
        send("POST", "/api/auth/login/", "1.1.1.1");

        assertThat(send("POST", "/api/auth/login//", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void otherAddressIsNotAffected() throws Exception {
        send("POST", "/api/auth/login", "1.1.1.1");
        send("POST", "/api/auth/login", "1.1.1.1");

        assertThat(send("POST", "/api/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPTIONS", "GET"})
    void nonPostRequestsAreNotCounted(String method) throws Exception {
        for (int i = 0; i < 5; i++) {
            send(method, "/api/auth/login", "1.1.1.1");
        }

        assertThat(send("POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/devices", "/api/devices/ingest", "/api/users"})
    void otherEndpointsAreNeverLimited(String path) throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", path, "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }
}
