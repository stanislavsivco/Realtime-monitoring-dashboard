package com.realtime_monitoring_dashboard.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.realtime_monitoring_dashboard.backend.dto.UserDTO;
import com.realtime_monitoring_dashboard.backend.exception.GlobalExceptionHandler;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    private static UserDTO userDto(Long id, String username, Role role) {
        return UserDTO.builder().id(id).username(username).role(role).build();
    }

    @Test
    void getAllUsersReturnsList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                userDto(1L, "admin", Role.ADMIN),
                userDto(2L, "john", Role.VIEWER)));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].username").value("john"))
                .andExpect(jsonPath("$[1].role").value("VIEWER"));
    }

    @Test
    void changeRoleUpdatesAndReturnsUser() throws Exception {
        when(userService.changeUserRole(eq(2L), eq(Role.ADMIN))).thenReturn(userDto(2L, "john", Role.ADMIN));
        String body = """
                {"role": "ADMIN"}
                """;

        mockMvc.perform(patch("/api/users/2/role").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        verify(userService).changeUserRole(2L, Role.ADMIN);
    }

    @Test
    void changeRoleWithInvalidRoleReturns400() throws Exception {
        String body = """
                {"role": "SUPERUSER"}
                """;

        mockMvc.perform(patch("/api/users/2/role").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void changeRoleWithNullRoleReturns400() throws Exception {
        String body = """
                {"role": null}
                """;

        mockMvc.perform(patch("/api/users/2/role").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void changeRoleForMissingUserReturns404() throws Exception {
        when(userService.changeUserRole(eq(99L), any(Role.class)))
                .thenThrow(new ResourceNotFoundException("User with ID 99 does not exist"));
        String body = """
                {"role": "ADMIN"}
                """;

        mockMvc.perform(patch("/api/users/99/role").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with ID 99 does not exist"));
    }


}
