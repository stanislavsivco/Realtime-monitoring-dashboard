package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtime_monitoring_dashboard.backend.dto.IngestMetricRequestDTO;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;

@ExtendWith(MockitoExtension.class)
class MetricSimulatorTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private MetricService metricService;

    @InjectMocks
    private MetricSimulator metricSimulator;

    private static Device device(Long id) {
        return Device.builder().id(id).name("Simulated-" + id).type("Server").location("DC1").simulated(true).build();
    }

    @Test
    void doesNothingWhenNoSimulatedDevicesExist() {
        when(deviceRepository.findBySimulatedTrue()).thenReturn(List.of());

        metricSimulator.generateMetrics();

        verify(metricService, never()).saveMetric(any(IngestMetricRequestDTO.class));
    }

    @Test
    void loadsOnlySimulatedDevicesSoRealDevicesAreNeverTouched() {
        when(deviceRepository.findBySimulatedTrue()).thenReturn(List.of(device(1L)));

        metricSimulator.generateMetrics();

        verify(deviceRepository).findBySimulatedTrue();
        verify(deviceRepository, never()).findAll();
    }

    @Test
    void sendsOneMetricPerDeviceThroughSaveMetricWithValuesInExpectedRanges() {
        when(deviceRepository.findBySimulatedTrue()).thenReturn(List.of(device(1L), device(2L)));

        metricSimulator.generateMetrics();

        ArgumentCaptor<IngestMetricRequestDTO> captor = ArgumentCaptor.forClass(IngestMetricRequestDTO.class);
        verify(metricService, times(2)).saveMetric(captor.capture());
        assertThat(captor.getAllValues()).extracting(IngestMetricRequestDTO::getDeviceId).containsExactly(1L, 2L);
        for (IngestMetricRequestDTO request : captor.getAllValues()) {
            assertThat(request.getCpu()).isBetween(10.0, 95.0);
            assertThat(request.getRam()).isBetween(20.0, 95.0);
            assertThat(request.getDisk()).isBetween(10.0, 95.0);
            assertThat(request.getLatencyMs()).isBetween(5, 150);
            assertThat(request.getNetworkInMbps()).isBetween(0.0, 500.0);
            assertThat(request.getNetworkOutMbps()).isBetween(0.0, 500.0);
        }
    }
}
