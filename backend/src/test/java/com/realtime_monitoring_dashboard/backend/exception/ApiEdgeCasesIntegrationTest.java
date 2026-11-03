package com.realtime_monitoring_dashboard.backend.exception;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.security.JwtService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apiedgecases;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=api-edge-cases-jwt-secret-that-is-long-enough",
        "agent.api-key=api-edge-cases-agent-key-that-is-long-enough",
        "admin.password=",
        "rate-limit.login-per-minute=1000",
        "rate-limit.register-per-hour=1000"
})
class ApiEdgeCasesIntegrationTest {

    private static final LocalDateTime OLD = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final LocalDateTime RECENT = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;
    private String adminAuthorization;
    private Device device;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        alertRepository.deleteAll();
        metricRepository.deleteAll();
        deviceRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder().username("edge-admin").password("unused").role(Role.ADMIN).build());
        adminAuthorization = "Bearer " + jwtService.generateToken("edge-admin", Role.ADMIN.name(), 0);

        device = deviceRepository.save(Device.builder()
                .name("Edge-Server").type("Server").location("DC1").status(DeviceStatus.ONLINE).simulated(false).build());
        metricRepository.save(metricAt(OLD));
        metricRepository.save(metricAt(RECENT));
    }

    private Metric metricAt(LocalDateTime timestamp) {
        return Metric.builder().device(device).timestamp(timestamp).cpu(10.0).ram(10.0).disk(10.0).build();
    }

    private String metricsPath() {
        return "/api/devices/" + device.getId() + "/metrics";
    }

    @Test
    void onlyStartDateFiltersMetrics() throws Exception {
        mockMvc.perform(get(metricsPath()).param("startDate", "2026-02-01T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].timestamp").value(startsWith("2026-03-01")));
    }

    @Test
    void onlyEndDateFiltersMetrics() throws Exception {
        mockMvc.perform(get(metricsPath()).param("endDate", "2026-02-01T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].timestamp").value(startsWith("2026-01-01")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/metrics", "/analytics"})
    void missingDeviceReturns404(String suffix) throws Exception {
        mockMvc.perform(get("/api/devices/999999" + suffix).header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownSortOnMetricsReturns400() throws Exception {
        mockMvc.perform(get(metricsPath()).param("sort", "foo").header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownSortOnAlertsReturns400() throws Exception {
        mockMvc.perform(get("/api/alerts").param("sort", "foo").header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void validSortStillWorks() throws Exception {
        mockMvc.perform(get(metricsPath()).param("sort", "timestamp,asc").header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].timestamp").value(startsWith("2026-01-01")));
        mockMvc.perform(get("/api/alerts").param("sort", "timestamp,asc").header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isOk());
    }

    @Test
    void wrongContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void unsupportedAcceptReturns406AsJson() throws Exception {
        mockMvc.perform(get("/api/devices").accept(MediaType.APPLICATION_XML)
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406));
    }

    @Test
    void validationErrorIsReturnedAsJsonEvenWithOtherAccept() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void createdDeviceIsRealAndOfflineEvenIfClientSendsStatus() throws Exception {
        mockMvc.perform(post("/api/devices").header(HttpHeaders.AUTHORIZATION, adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New-Router", "type": "Router", "location": "KE", "status": "CRITICAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.simulated").value(false));
    }

    @Test
    void updatingDeviceKeepsItsStatusEvenIfClientSendsOne() throws Exception {
        mockMvc.perform(put("/api/devices/" + device.getId()).header(HttpHeaders.AUTHORIZATION, adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Renamed", "type": "Server", "location": "DC2", "status": "CRITICAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.status").value("ONLINE"));
    }
}
