package com.realtime_monitoring_dashboard.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.realtime_monitoring_dashboard.backend.dto.AlertDTO;
import com.realtime_monitoring_dashboard.backend.exception.GlobalExceptionHandler;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.service.AlertService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertControllerTest {

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertController alertController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(alertController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private static AlertDTO alertDto(boolean acknowledged, boolean resolved) {
        return AlertDTO.builder().id(1L).deviceId(2L).deviceName("Server-1")
                .severity(AlertSeverity.CRITICAL).message("CPU too high")
                .timestamp(LocalDateTime.of(2026, 9, 1, 12, 0))
                .acknowledged(acknowledged).resolved(resolved).build();
    }

    @Test
    void getAlertsPassesFiltersAndReturnsPage() throws Exception {
        when(alertService.getAlertsPaged(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alertDto(false, false)), PageRequest.of(0, 15), 1));

        mockMvc.perform(get("/api/alerts").param("resolved", "false").param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].deviceName").value("Server-1"));

        verify(alertService).getAlertsPaged(eq(Boolean.FALSE), eq(AlertSeverity.CRITICAL), any(Pageable.class));
    }

    @Test
    void getAlertsWithoutFiltersPassesNulls() throws Exception {
                when(alertService.getAlertsPaged(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0));

        mockMvc.perform(get("/api/alerts")).andExpect(status().isOk());

        verify(alertService).getAlertsPaged(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getAlertsWithUnknownSeverityReturns400() throws Exception {
        mockMvc.perform(get("/api/alerts").param("severity", "INFO"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void acknowledgeAlertReturnsUpdatedAlert() throws Exception {
        when(alertService.acknowledgeAlert(1L)).thenReturn(alertDto(true, false));

        mockMvc.perform(patch("/api/alerts/1/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(true));
    }

    @Test
    void acknowledgeAlertReturns404WhenMissing() throws Exception {
        when(alertService.acknowledgeAlert(99L))
                .thenThrow(new ResourceNotFoundException("Alert with ID 99 does not exist"));

        mockMvc.perform(patch("/api/alerts/99/acknowledge"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveAlertReturnsUpdatedAlert() throws Exception {
        when(alertService.resolveAlert(1L)).thenReturn(alertDto(false, true));

        mockMvc.perform(patch("/api/alerts/1/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true));
    }

    @Test
    void resolveAlertReturns404WhenMissing() throws Exception {
        when(alertService.resolveAlert(99L))
                .thenThrow(new ResourceNotFoundException("Alert with ID 99 does not exist"));

        mockMvc.perform(patch("/api/alerts/99/resolve"))
                .andExpect(status().isNotFound());
    }
}