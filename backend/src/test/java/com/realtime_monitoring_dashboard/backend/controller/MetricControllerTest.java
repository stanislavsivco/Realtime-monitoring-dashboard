package com.realtime_monitoring_dashboard.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO;
import com.realtime_monitoring_dashboard.backend.exception.GlobalExceptionHandler;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.service.MetricService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricControllerTest {

    @Mock
    private MetricService metricService;

    @InjectMocks
    private MetricController metricController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(metricController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private static MetricDTO metricDto() {
        return MetricDTO.builder().id(1L).deviceId(1L).timestamp(LocalDateTime.of(2026, 9, 1, 12, 0))
                .cpu(45.5).ram(60.0).disk(30.0).latencyMs(20)
                .networkInMbps(100.0).networkOutMbps(50.0).build();
    }

    @Test
    void getMetricsUsesDefaultPagingAndReturnsPage() throws Exception {
        when(metricService.getMetricsByDeviceIdPaged(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(metricDto()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/devices/1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].networkInMbps").value(100.0));
    }

    @Test
    void getMetricsParsesDateRange() throws Exception {
        when(metricService.getMetricsByDeviceIdPaged(any(Long.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/devices/1/metrics")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-30T23:59:59"))
                .andExpect(status().isOk());

        verify(metricService).getMetricsByDeviceIdPaged(eq(1L),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 30, 23, 59, 59)),
                any(Pageable.class));
    }

    @Test
    void getMetricsWithInvalidDateReturns400() throws Exception {
        mockMvc.perform(get("/api/devices/1/metrics").param("startDate", "yesterday"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(metricService);
    }

    @Test
    void getLatestMetricReturnsMetric() throws Exception {
        when(metricService.getLatestMetricByDeviceId(1L)).thenReturn(metricDto());

        mockMvc.perform(get("/api/devices/1/metrics/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ram").value(60.0));
    }

    @Test
    void getLatestMetricReturns404WhenMissing() throws Exception {
        when(metricService.getLatestMetricByDeviceId(99L))
                .thenThrow(new ResourceNotFoundException("Device with ID 99 does not have any metrics or does not exist"));

        mockMvc.perform(get("/api/devices/99/metrics/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void analyticsReturnsSummary() throws Exception {
        MetricSummaryDTO summary = MetricSummaryDTO.builder()
                .avgCpu(40.0).maxCpu(90.0).minCpu(10.0).avgLatencyMs(35.0).build();
        when(metricService.getMetricSummary(eq(1L), isNull(), isNull())).thenReturn(summary);

        mockMvc.perform(get("/api/devices/1/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgCpu").value(40.0));
    }

    @Test
    void ingestValidMetricReturns201() throws Exception {
        String body = """
                {"deviceId": 1, "cpu": 45.5, "ram": 60.0, "disk": 30.0, "latencyMs": 20,
                 "networkInMbps": 100.0, "networkOutMbps": 50.0}
                """;

        mockMvc.perform(post("/api/devices/ingest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        verify(metricService).saveMetric(any(MetricDTO.class));
    }

    @Test
    void ingestForUnknownDeviceReturns404() throws Exception {
        when(metricService.saveMetric(any(MetricDTO.class)))
                .thenThrow(new ResourceNotFoundException("Device with ID 99 does not exist"));
        String body = """
                {"deviceId": 99, "cpu": 10.0, "ram": 10.0, "disk": 10.0}
                """;

        mockMvc.perform(post("/api/devices/ingest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void ingestWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/devices/ingest").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(metricService);
    }
}
