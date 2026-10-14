package com.realtime_monitoring_dashboard.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.realtime_monitoring_dashboard.backend.dto.AuthResponseDTO;
import com.realtime_monitoring_dashboard.backend.dto.LoginRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.GlobalExceptionHandler;
import com.realtime_monitoring_dashboard.backend.exception.UsernameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    private static AuthResponseDTO authResponse(String username) {
        return AuthResponseDTO.builder()
                .token("jwt.token.here")
                .username(username)
                .role(Role.VIEWER)
                .build();
    }

    @Test
    void registerReturns201WithToken() throws Exception {
        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(authResponse("john"));
        String body = """
                {"username": "john", "password": "securepass1"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.role").value("VIEWER"));

        verify(authService).register(any(RegisterRequestDTO.class));
    }

    @Test
    void registerWithBlankUsernameReturns400() throws Exception {
        String body = """
                {"username": "  ", "password": "securepass1"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithPasswordTooShortReturns400() throws Exception {
        String body = """
                {"username": "john", "password": "short"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithPasswordTooLongReturns400() throws Exception {
        String longPassword = "a".repeat(73);
        String body = String.format("{\"username\": \"john\", \"password\": \"%s\"}", longPassword);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithDuplicateUsernameReturns409() throws Exception {
        when(authService.register(any(RegisterRequestDTO.class)))
                .thenThrow(new UsernameAlreadyExistsException("Username 'john' is already taken"));
        String body = """
                {"username": "john", "password": "securepass1"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username 'john' is already taken"));
    }

    @Test
    void loginReturns200WithToken() throws Exception {
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(authResponse("john"));
        String body = """
                {"username": "john", "password": "securepass1"}
                """;

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.here"));

        verify(authService).login(any(LoginRequestDTO.class));
    }

    @Test
    void loginWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}
