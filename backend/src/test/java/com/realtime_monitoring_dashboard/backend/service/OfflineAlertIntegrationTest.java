package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.realtime_monitoring_dashboard.backend.dto.IngestMetricRequestDTO;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import com.realtime_monitoring_dashboard.backend.support.PostgresTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ThresholdService.class, MetricService.class, DeviceStatusCalculator.class, AlertService.class})
class OfflineAlertIntegrationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    private MetricService metricService;
    @Autowired
    private AlertService alertService;
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

    private Device deviceWithStaleMetric(String name, DeviceStatus status) {
        Device device = deviceRepository.save(Device.builder()
                .name(name).type("Server").location("DC1").status(status).build());
        metricRepository.save(Metric.builder()
                .device(device).timestamp(LocalDateTime.now().minusMinutes(5)).cpu(10.0).ram(10.0).disk(10.0).build());
        entityManager.flush();
        return device;
    }

    private List<Alert> activeAlerts(Device device) {
        return alertRepository.findByDeviceIdAndResolvedFalse(device.getId());
    }

    private DeviceStatus statusOf(Device device) {
        return deviceRepository.findById(device.getId()).orElseThrow().getStatus();
    }

    private void sendMetric(Device device, double cpu) {
        metricService.saveMetric(IngestMetricRequestDTO.builder()
                .deviceId(device.getId()).cpu(cpu).ram(10.0).disk(10.0).latencyMs(5).build());
    }

    @Test
    void deviceThatStopsReportingGetsOneCriticalOfflineAlert() {
        Device device = deviceWithStaleMetric("Offline-Server", DeviceStatus.ONLINE);

        metricService.checkOfflineDevices();

        assertThat(statusOf(device)).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(activeAlerts(device)).singleElement().satisfies(alert -> {
            assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
            assertThat(alert.getMessage()).isEqualTo("Device Offline-Server is offline: no metrics for over 30 seconds");
        });
    }

    @Test
    void goingOfflineReplacesOldLoadAlert() {
        Device device = deviceWithStaleMetric("Loaded-Server", DeviceStatus.WARNING);
        alertService.createAlert(device, AlertSeverity.WARNING, "Load on Loaded-Server");

        metricService.checkOfflineDevices();

        assertThat(activeAlerts(device)).singleElement()
                .satisfies(alert -> assertThat(alert.getMessage()).contains("is offline"));
        assertThat(alertRepository.count()).isEqualTo(2);
    }

    @Test
    void stayingOfflineDoesNotDuplicateTheAlert() {
        deviceWithStaleMetric("Offline-Server", DeviceStatus.ONLINE);

        metricService.checkOfflineDevices();
        metricService.checkOfflineDevices();

        assertThat(alertRepository.findAll()).hasSize(1);
    }

    @Test
    void resolvedOfflineAlertDoesNotComeBackWhileDeviceStaysOffline() {
        Device device = deviceWithStaleMetric("Offline-Server", DeviceStatus.ONLINE);
        metricService.checkOfflineDevices();
        alertService.resolveAlert(activeAlerts(device).get(0).getId());

        metricService.checkOfflineDevices();

        assertThat(activeAlerts(device)).isEmpty();
    }

    @Test
    void comingBackHealthyResolvesTheOfflineAlert() {
        Device device = deviceWithStaleMetric("Offline-Server", DeviceStatus.ONLINE);
        metricService.checkOfflineDevices();

        sendMetric(device, 10.0);

        assertThat(statusOf(device)).isEqualTo(DeviceStatus.ONLINE);
        assertThat(activeAlerts(device)).isEmpty();
    }

    @Test
    void comingBackOverloadedReplacesTheOfflineAlertWithLoadAlert() {
        Device device = deviceWithStaleMetric("Offline-Server", DeviceStatus.ONLINE);
        metricService.checkOfflineDevices();

        sendMetric(device, 99.0);

        assertThat(statusOf(device)).isEqualTo(DeviceStatus.CRITICAL);
        assertThat(activeAlerts(device)).singleElement()
                .satisfies(alert -> assertThat(alert.getMessage()).startsWith("Load on"));
    }

    @Test
    void deviceThatNeverReportedGoesOfflineWithoutAlert() {
        Device device = deviceRepository.save(Device.builder()
                .name("New-Router").type("Router").location("DC1").status(DeviceStatus.ONLINE).build());

        metricService.checkOfflineDevices();

        assertThat(statusOf(device)).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(alertRepository.count()).isZero();
    }

    @Test
    void longDeviceNameDoesNotBlockOfflineAlert() {
        Device device = deviceWithStaleMetric("x".repeat(255), DeviceStatus.ONLINE);

        metricService.checkOfflineDevices();
        entityManager.flush();

        assertThat(statusOf(device)).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(activeAlerts(device)).singleElement()
                .satisfies(alert -> assertThat(alert.getMessage()).hasSize(AlertService.MAX_MESSAGE_LENGTH));
    }

    @Test
    void longDeviceNameDoesNotLoseMetricWithLoadAlert() {
        Device device = deviceWithStaleMetric("y".repeat(255), DeviceStatus.ONLINE);

        sendMetric(device, 99.0);
        entityManager.flush();

        assertThat(metricRepository.findByDeviceId(device.getId(), Pageable.unpaged()).getTotalElements()).isEqualTo(2);
        assertThat(statusOf(device)).isEqualTo(DeviceStatus.CRITICAL);
    }
}