package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registerrole;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=register-role-it-jwt-secret-that-is-long-enough",
        "agent.api-key=register-role-it-agent-key-that-is-long-enough",
        "admin.password=",
        "rate-limit.login-per-minute=1000",
        "rate-limit.register-per-hour=1000"
})
class RegisterRoleIntegrationTest {

    private static final String USERNAME = "role.test";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"role\": \"ADMIN\"",
            "\"role\": \"admin\"",
            "\"roles\": [\"ADMIN\"]",
            "\"admin\": true"
    })
    void registerAlwaysCreatesViewerEvenIfRequestContainsRole(String extraField) throws Exception {
        String body = "{\"username\": \"" + USERNAME + "\", \"password\": \"password123\", " + extraField + "}";

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andReturn();

        User saved = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.VIEWER);

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/devices/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}