package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.realtime_monitoring_dashboard.backend.controller.DeviceController;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.service.AlertService;
import com.realtime_monitoring_dashboard.backend.service.AuthService;
import com.realtime_monitoring_dashboard.backend.service.DeviceService;
import com.realtime_monitoring_dashboard.backend.service.MetricService;
import com.realtime_monitoring_dashboard.backend.service.ThresholdService;
import com.realtime_monitoring_dashboard.backend.service.UserService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apisecurity;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=" + ApiSecurityIntegrationTest.JWT_SECRET,
        "agent.api-key=" + ApiSecurityIntegrationTest.AGENT_KEY,
        "admin.password=",
        "rate-limit.login-per-minute=1000",
        "rate-limit.register-per-hour=1000"
})
class ApiSecurityIntegrationTest {

    static final String JWT_SECRET = "security-it-jwt-secret-that-is-long-enough";
    static final String AGENT_KEY = "security-it-agent-key-that-is-long-enough";
    private static final String FOREIGN_SECRET = "another-secret-that-is-long-enough-xx";

    private static final String ADMIN_USERNAME = "sec-admin";
    private static final String VIEWER_USERNAME = "sec-viewer";

    private static final String LOGIN_BODY = """
            {"username": "someone", "password": "password123"}
            """;
    private static final String REGISTER_BODY = """
            {"username": "new.user", "password": "password123"}
            """;
    private static final String DEVICE_BODY = """
            {"name": "Router-1", "type": "Router", "location": "Kosice", "status": "ONLINE"}
            """;
    private static final String THRESHOLD_BODY = """
            {"cpuWarning": 70, "cpuCritical": 90, "ramWarning": 70, "ramCritical": 90,
             "diskWarning": 70, "diskCritical": 90, "latencyWarningMs": 100, "latencyCriticalMs": 200}
            """;
    private static final String ROLE_BODY = """
            {"role": "VIEWER"}
            """;
    private static final String AGENT_REGISTER_BODY = """
            {"hostname": "host-1", "type": "Server", "location": "DC1"}
            """;
    private static final String INGEST_BODY = """
            {"deviceId": 1, "cpu": 10, "ram": 20, "disk": 30}
            """;

    private enum Access { PUBLIC, AGENT, AUTHENTICATED, ADMIN }

    private enum BadCredential { MALFORMED_TOKEN, FOREIGN_SECRET_TOKEN, EXPIRED_TOKEN, UNKNOWN_USER_TOKEN, BASIC_SCHEME }

    private record Endpoint(HttpMethod method, String template, Access access, String body) {

        String path() {
            return template.replace("{id}", "1");
        }

        String key() {
            return method.name() + " " + template;
        }

        @Override
        public String toString() {
            return key() + " [" + access + "]";
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.POST, "/api/auth/login", Access.PUBLIC, LOGIN_BODY),
            new Endpoint(HttpMethod.POST, "/api/auth/register", Access.PUBLIC, REGISTER_BODY),
            new Endpoint(HttpMethod.GET, "/api/devices", Access.AUTHENTICATED, null),
            new Endpoint(HttpMethod.GET, "/api/devices/{id}", Access.AUTHENTICATED, null),
            new Endpoint(HttpMethod.POST, "/api/devices", Access.ADMIN, DEVICE_BODY),
            new Endpoint(HttpMethod.PUT, "/api/devices/{id}", Access.ADMIN, DEVICE_BODY),
            new Endpoint(HttpMethod.DELETE, "/api/devices/{id}", Access.ADMIN, null),
            new Endpoint(HttpMethod.POST, "/api/devices/register", Access.AGENT, AGENT_REGISTER_BODY),
            new Endpoint(HttpMethod.POST, "/api/devices/ingest", Access.AGENT, INGEST_BODY),
            new Endpoint(HttpMethod.GET, "/api/devices/{id}/metrics", Access.AUTHENTICATED, null),
            new Endpoint(HttpMethod.GET, "/api/devices/{id}/metrics/latest", Access.AUTHENTICATED, null),
            new Endpoint(HttpMethod.GET, "/api/devices/{id}/analytics", Access.AUTHENTICATED, null),
            new Endpoint(HttpMethod.GET, "/api/devices/{id}/thresholds", Access.AUTHENTICATED, null),
            new Endpoint(HttpMethod.PUT, "/api/devices/{id}/thresholds", Access.ADMIN, THRESHOLD_BODY),
            new Endpoint(HttpMethod.GET, "/api/alerts", Access.AUTHENTICATED, null),
            new Endpoint(HttpMethod.PATCH, "/api/alerts/{id}/acknowledge", Access.ADMIN, null),
            new Endpoint(HttpMethod.PATCH, "/api/alerts/{id}/resolve", Access.ADMIN, null),
            new Endpoint(HttpMethod.GET, "/api/users", Access.ADMIN, null),
            new Endpoint(HttpMethod.PATCH, "/api/users/{id}/role", Access.ADMIN, ROLE_BODY)
    );

    private static Stream<Endpoint> endpoints(Access... access) {
        Set<Access> wanted = EnumSet.copyOf(Arrays.asList(access));
        return ENDPOINTS.stream().filter(endpoint -> wanted.contains(endpoint.access()));
    }

    static Stream<Endpoint> publicEndpoints() {
        return endpoints(Access.PUBLIC);
    }

    static Stream<Endpoint> nonPublicEndpoints() {
        return endpoints(Access.AGENT, Access.AUTHENTICATED, Access.ADMIN);
    }

    static Stream<Endpoint> agentEndpoints() {
        return endpoints(Access.AGENT);
    }

    static Stream<Endpoint> userEndpoints() {
        return endpoints(Access.AUTHENTICATED, Access.ADMIN);
    }

    static Stream<Endpoint> authenticatedEndpoints() {
        return endpoints(Access.AUTHENTICATED);
    }

    static Stream<Endpoint> adminEndpoints() {
        return endpoints(Access.ADMIN);
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AlertService alertService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private DeviceService deviceService;
    @MockitoBean
    private MetricService metricService;
    @MockitoBean
    private ThresholdService thresholdService;
    @MockitoBean
    private UserService userService;

    private MockMvc mockMvc;
    private String adminToken;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        userRepository.deleteAll();
        userRepository.save(User.builder().username(ADMIN_USERNAME).password("unused").role(Role.ADMIN).build());
        userRepository.save(User.builder().username(VIEWER_USERNAME).password("unused").role(Role.VIEWER).build());

        adminToken = jwtService.generateToken(ADMIN_USERNAME, Role.ADMIN.name());
        viewerToken = jwtService.generateToken(VIEWER_USERNAME, Role.VIEWER.name());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions perform(Endpoint endpoint, String authorization, String agentKey) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (agentKey != null) {
            builder.header(AgentApiKeyFilter.HEADER, agentKey);
        }
        return mockMvc.perform(builder);
    }

    private static void assertAllowed(ResultActions result) throws Exception {
        assertThat(result.andReturn().getResponse().getStatus()).isNotIn(401, 403);
    }

    private static void assertRejected(ResultActions result, int expectedStatus) throws Exception {
        result.andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private void assertServicesUntouched() {
        verifyNoInteractions(alertService, authService, deviceService, thresholdService, userService);
        assertThat(mockingDetails(metricService).getInvocations())
                .allSatisfy(invocation -> assertThat(invocation.getMethod().getName()).isEqualTo("checkOfflineDevices"));
    }

    private String authorizationFor(BadCredential credential) {
        SecretKey foreignKey = Keys.hmacShaKeyFor(FOREIGN_SECRET.getBytes(StandardCharsets.UTF_8));
        SecretKey ownKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();

        return switch (credential) {
            case MALFORMED_TOKEN -> bearer("not.a.jwt");
            case FOREIGN_SECRET_TOKEN -> bearer(Jwts.builder()
                    .subject(ADMIN_USERNAME)
                    .claim("role", "ADMIN")
                    .issuedAt(new Date(now))
                    .expiration(new Date(now + 3_600_000))
                    .signWith(foreignKey)
                    .compact());
            case EXPIRED_TOKEN -> bearer(Jwts.builder()
                    .subject(ADMIN_USERNAME)
                    .claim("role", "ADMIN")
                    .issuedAt(new Date(now - 7_200_000))
                    .expiration(new Date(now - 3_600_000))
                    .signWith(ownKey)
                    .compact());
            case UNKNOWN_USER_TOKEN -> bearer(jwtService.generateToken("ghost-user", "ADMIN"));
            case BASIC_SCHEME -> "Basic c2VjLWFkbWluOnVudXNlZA==";
        };
    }

    @Test
    void endpointMatrixCoversEveryControllerEndpoint() {
        String controllerPackage = DeviceController.class.getPackageName();
        Set<String> actual = new TreeSet<>();

        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            if (handlerMethod.getBeanType().getPackageName().equals(controllerPackage)) {
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                        actual.add(method.name() + " " + pattern);
                    }
                }
            }
        });

        Set<String> expected = ENDPOINTS.stream()
                .map(Endpoint::key)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(actual)
                .as("every controller endpoint must be listed in ENDPOINTS with its expected access level")
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicEndpoints")
    void anonymousCanCallPublicEndpoints(Endpoint endpoint) throws Exception {
        assertAllowed(perform(endpoint, null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonPublicEndpoints")
    void anonymousIsRejectedWithJson401(Endpoint endpoint) throws Exception {
        assertRejected(perform(endpoint, null, null), 401);
        assertServicesUntouched();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authenticatedEndpoints")
    void viewerCanCallReadEndpoints(Endpoint endpoint) throws Exception {
        assertAllowed(perform(endpoint, bearer(viewerToken), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adminEndpoints")
    void viewerIsRejectedWithJson403OnAdminEndpoints(Endpoint endpoint) throws Exception {
        assertRejected(perform(endpoint, bearer(viewerToken), null), 403);
        assertServicesUntouched();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("userEndpoints")
    void adminCanCallEveryUserEndpoint(Endpoint endpoint) throws Exception {
        assertAllowed(perform(endpoint, bearer(adminToken), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("agentEndpoints")
    void agentEndpointsAcceptValidKeyWithoutJwt(Endpoint endpoint) throws Exception {
        assertAllowed(perform(endpoint, null, AGENT_KEY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("agentEndpoints")
    void agentEndpointsRejectMissingOrWrongKeyEvenWithAdminJwt(Endpoint endpoint) throws Exception {
        assertRejected(perform(endpoint, null, null), 401);
        assertRejected(perform(endpoint, bearer(adminToken), null), 401);
        assertRejected(perform(endpoint, null, "wrong-key"), 401);
        assertRejected(perform(endpoint, null, AGENT_KEY + "x"), 401);
        assertRejected(perform(endpoint, null, AGENT_KEY.substring(1)), 401);
        assertRejected(perform(endpoint, null, ""), 401);
        assertServicesUntouched();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("userEndpoints")
    void agentKeyDoesNotGrantAccessToUserEndpoints(Endpoint endpoint) throws Exception {
        assertRejected(perform(endpoint, null, AGENT_KEY), 401);
        assertServicesUntouched();
    }

    @ParameterizedTest
    @EnumSource(BadCredential.class)
    void invalidCredentialsAreTreatedAsAnonymous(BadCredential credential) throws Exception {
        String authorization = authorizationFor(credential);

        for (Endpoint endpoint : ENDPOINTS) {
            if (endpoint.access() == Access.AUTHENTICATED || endpoint.access() == Access.ADMIN) {
                assertRejected(perform(endpoint, authorization, null), 401);
            }
        }
        assertServicesUntouched();
    }

    @Test
    void viewerCannotDeleteDevice() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/devices/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deviceService);
    }

    @Test
    void adminCanDeleteDevice() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/devices/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void roleClaimInsideTheTokenIsIgnored() throws Exception {
        String viewerTokenClaimingAdmin = jwtService.generateToken(VIEWER_USERNAME, Role.ADMIN.name());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/devices/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerTokenClaimingAdmin)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deviceService);
    }

    @Test
    void demotedAdminLosesAccessWithTheSameToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/devices/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        User admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();
        admin.setRole(Role.VIEWER);
        userRepository.save(admin);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/devices/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenOfDeletedUserIsRejected() throws Exception {
        userRepository.delete(userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/devices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathsRequireAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/devices/ingest/extra")
                        .contentType(MediaType.APPLICATION_JSON).content(INGEST_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void viewerGetsDevicesWith200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/devices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void ingestWithValidAgentKeyReturns201() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/devices/ingest")
                        .header(AgentApiKeyFilter.HEADER, AGENT_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(INGEST_BODY))
                .andExpect(status().isCreated());
    }
}