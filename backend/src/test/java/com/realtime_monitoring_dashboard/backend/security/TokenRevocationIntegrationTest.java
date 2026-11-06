package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.support.PostgresTestDatabase;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(properties = {
        "jwt.secret=" + TokenRevocationIntegrationTest.JWT_SECRET,
        "agent.api-key=token-revocation-agent-key-that-is-long-enough",
        "admin.password=",
        "rate-limit.login-per-minute=1000",
        "rate-limit.register-per-hour=1000"
})
class TokenRevocationIntegrationTest {

    static final String JWT_SECRET = "token-revocation-jwt-secret-that-is-long-enough";
    private static final String PASSWORD = "password123";

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.deleteAll();
    }

    private String credentials(String username) {
        return "{\"username\": \"" + username + "\", \"password\": \"" + PASSWORD + "\"}";
    }

    private String tokenFrom(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String register(String username) throws Exception {
        return tokenFrom(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username)))
                .andExpect(status().isCreated()));
    }

    private String login(String username) throws Exception {
        return tokenFrom(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username)))
                .andExpect(status().isOk()));
    }

    private ResultActions devices(String token) throws Exception {
        return mockMvc.perform(get("/api/devices").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions logout(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void logoutRevokesTheTokenImmediately() throws Exception {
        String token = register("revoke.me");
        devices(token).andExpect(status().isOk());

        logout(token).andExpect(status().isNoContent());

        devices(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void logoutRevokesEveryTokenOfTheUser() throws Exception {
        String first = register("two.devices");
        String second = login("two.devices");

        logout(first).andExpect(status().isNoContent());

        devices(first).andExpect(status().isUnauthorized());
        devices(second).andExpect(status().isUnauthorized());
    }

    @Test
    void loginAfterLogoutGivesWorkingTokenAndOldOneStaysRevoked() throws Exception {
        String old = register("comes.back");
        logout(old).andExpect(status().isNoContent());

        String fresh = login("comes.back");

        devices(fresh).andExpect(status().isOk());
        devices(old).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDoesNotAffectOtherUsers() throws Exception {
        String leaving = register("leaving.user");
        String staying = register("staying.user");

        logout(leaving).andExpect(status().isNoContent());

        devices(staying).andExpect(status().isOk());
    }

    @Test
    void revokedTokenCannotLogOutAgain() throws Exception {
        String token = register("twice.out");
        logout(token).andExpect(status().isNoContent());

        logout(token).andExpect(status().isUnauthorized());

        assertThat(userRepository.findByUsernameIgnoreCase("twice.out").orElseThrow().getTokenVersion()).isEqualTo(1);
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithWrongVersionIsRejected() throws Exception {
        register("wrong.version");
        String forged = jwtService.generateToken("wrong.version", "VIEWER", 5);

        devices(forged).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIssuedBeforeThisFeatureIsRejected() throws Exception {
        register("old.token");
        String oldToken = Jwts.builder()
                .subject("old.token")
                .claim("role", "VIEWER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        devices(oldToken).andExpect(status().isUnauthorized());
    }
}