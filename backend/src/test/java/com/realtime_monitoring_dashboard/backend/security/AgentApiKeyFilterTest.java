package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class AgentApiKeyFilterTest {

    private static final String KEY = "agent-key-that-is-at-least-32-bytes-long";

    private final SecurityErrorWriter errorWriter = new SecurityErrorWriter(JsonMapper.builder().build());
    private final AgentApiKeyFilter filter = new AgentApiKeyFilter(KEY, errorWriter);

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void validKeyPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices/ingest");
        request.addHeader(AgentApiKeyFilter.HEADER, KEY);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingKeyReturns401WithJsonBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices/register");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"message\":\"Invalid or missing agent API key\"")
                .contains("\"path\":\"/api/devices/register\"");
    }

    @Test
    void emptyKeyHeaderIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices/ingest");
        request.addHeader(AgentApiKeyFilter.HEADER, "");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices/ingest");
        request.addHeader(AgentApiKeyFilter.HEADER, KEY + "x");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void trailingSlashIsStillProtected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices/ingest/");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void pathBehindContextPathIsStillProtected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/backend/api/devices/ingest");
        request.setContextPath("/backend");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void otherPathsAreNotChecked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockFilterChain chain = new MockFilterChain();

        run(request, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void constructorRejectsBlankKey() {
        assertThatThrownBy(() -> new AgentApiKeyFilter("", errorWriter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGENT_API_KEY must be set");
    }

    @Test
    void constructorRejectsShortKey() {
        assertThatThrownBy(() -> new AgentApiKeyFilter("short-key", errorWriter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
