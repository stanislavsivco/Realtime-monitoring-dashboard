package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.UpdateThresholdRequestDTO;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ThresholdService.class, MetricService.class, DeviceStatusCalculator.class, AlertService.class})
class ThresholdStatusIntegrationTest {

    @Autowired
    private ThresholdService thresholdService;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Device device;

    @BeforeEach
    void deviceWithFreshMetricAt50PercentCpu() {
        device = deviceRepository.save(Device.builder()
                .name("Threshold-Server").type("Server").location("DC1").status(DeviceStatus.ONLINE).build());
        metricRepository.save(Metric.builder()
                .device(device).timestamp(LocalDateTime.now()).cpu(50.0).ram(20.0).disk(20.0).latencyMs(10).build());
        entityManager.flush();
    }

    private static UpdateThresholdRequestDTO cpuThresholds(double warning, double critical) {
        return new UpdateThresholdRequestDTO(warning, critical, 75.0, 90.0, 75.0, 90.0, 200, 500);
    }

    private List<Alert> activeAlerts() {
        return alertRepository.findByDeviceIdAndResolvedFalse(device.getId());
    }

    @Test
    void loweringCpuCriticalBelowCurrentValueMakesDeviceCriticalWithAlert() {
        thresholdService.updateThreshold(device.getId(), cpuThresholds(30.0, 40.0));

        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getStatus()).isEqualTo(DeviceStatus.CRITICAL);
        assertThat(activeAlerts()).extracting(Alert::getSeverity).containsExactly(AlertSeverity.CRITICAL);
        verify(messagingTemplate).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void raisingThresholdsAgainMakesDeviceOnlineAndResolvesTheAlert() {
        thresholdService.updateThreshold(device.getId(), cpuThresholds(30.0, 40.0));

        thresholdService.updateThreshold(device.getId(), cpuThresholds(75.0, 90.0));

        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(activeAlerts()).isEmpty();
    }

    @Test
    void savingTheSameThresholdsTwiceDoesNotDuplicateAlerts() {
        thresholdService.updateThreshold(device.getId(), cpuThresholds(40.0, 60.0));
        thresholdService.updateThreshold(device.getId(), cpuThresholds(40.0, 60.0));

        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getStatus()).isEqualTo(DeviceStatus.WARNING);
        assertThat(activeAlerts()).hasSize(1);
    }

    @Test
    void offlineDeviceStaysOfflineAndGetsNoAlert() {
        device.setStatus(DeviceStatus.OFFLINE);
        deviceRepository.save(device);

        thresholdService.updateThreshold(device.getId(), cpuThresholds(30.0, 40.0));

        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getStatus()).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(activeAlerts()).isEmpty();
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void metricsAreNotDuplicatedByRecalculation() {
        thresholdService.updateThreshold(device.getId(), cpuThresholds(30.0, 40.0));

        assertThat(metricRepository.count()).isEqualTo(1);
    }
}
