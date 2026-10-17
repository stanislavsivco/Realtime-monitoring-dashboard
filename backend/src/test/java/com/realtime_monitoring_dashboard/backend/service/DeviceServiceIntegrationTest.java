package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.DeviceNameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.model.Threshold;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import com.realtime_monitoring_dashboard.backend.repository.ThresholdRepository;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(DeviceService.class)
class DeviceServiceIntegrationTest {

    @Autowired
    private DeviceService deviceService;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private ThresholdRepository thresholdRepository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Device deviceWithMetricAlertAndThreshold(String name) {
        Device device = deviceRepository.save(Device.builder()
                .name(name).type("Server").location("DC1").status(DeviceStatus.CRITICAL).build());

        metricRepository.save(Metric.builder()
                .device(device).timestamp(LocalDateTime.now()).cpu(95.0).ram(50.0).disk(40.0).build());
        alertRepository.save(Alert.builder()
                .device(device).severity(AlertSeverity.CRITICAL).message("CPU high")
                .timestamp(LocalDateTime.now()).resolved(false).build());
        thresholdRepository.save(Threshold.builder()
                .device(device).cpuWarning(70.0).cpuCritical(90.0).ramWarning(70.0).ramCritical(90.0)
                .diskWarning(70.0).diskCritical(90.0).latencyWarningMs(100).latencyCriticalMs(200).build());
        entityManager.flush();
        entityManager.clear();
        return device;
    }

    @Test
    void deletingDeviceDirectlyFailsWhileAlertsAndThresholdsReferenceIt() {
        Device device = deviceWithMetricAlertAndThreshold("plain-delete");

        assertThrows(DataIntegrityViolationException.class, () -> {
            deviceRepository.deleteById(device.getId());
            deviceRepository.flush();
        });
    }

    @Test
    void deleteDeviceRemovesDeviceWithMetricsAlertsAndThreshold() {
        Device device = deviceWithMetricAlertAndThreshold("service-delete");
        Long id = device.getId();

        deviceService.deleteDevice(id);

        assertThat(deviceRepository.existsById(id)).isFalse();
        assertThat(metricRepository.findTopByDeviceIdOrderByTimestampDesc(id)).isEmpty();
        assertThat(alertRepository.findByDeviceIdAndResolvedFalse(id)).isEmpty();
        assertThat(thresholdRepository.findByDeviceId(id)).isEmpty();
    }

    @Test
    void deleteDeviceKeepsDataOfOtherDevices() {
        Device toDelete = deviceWithMetricAlertAndThreshold("delete-me");
        Device toKeep = deviceWithMetricAlertAndThreshold("keep-me");

        deviceService.deleteDevice(toDelete.getId());

        assertThat(deviceRepository.existsById(toKeep.getId())).isTrue();
        assertThat(metricRepository.findTopByDeviceIdOrderByTimestampDesc(toKeep.getId())).isPresent();
        assertThat(alertRepository.findByDeviceIdAndResolvedFalse(toKeep.getId())).hasSize(1);
        assertThat(thresholdRepository.findByDeviceId(toKeep.getId())).isPresent();
    }

    @Test
    void databaseRejectsDuplicateDeviceName() {
        deviceService.createDevice(new CreateDeviceRequestDTO("unique-name", "Server", "DC1", DeviceStatus.ONLINE));

        assertThrows(DeviceNameAlreadyExistsException.class, () -> deviceService.createDevice(
                new CreateDeviceRequestDTO("unique-name", "Server", "DC2", DeviceStatus.ONLINE)));
        assertThrows(DataIntegrityViolationException.class, () -> deviceRepository.saveAndFlush(Device.builder()
                .name("unique-name").type("Server").location("DC3").status(DeviceStatus.ONLINE).build()));
    }

    @Test
    void registerAgentTwiceReturnsSameDevice() {
        Long first = deviceService.registerAgent(new RegisterAgentRequestDTO("agent-host", null, null)).getId();
        Long second = deviceService.registerAgent(new RegisterAgentRequestDTO("agent-host", null, null)).getId();

        assertThat(second).isEqualTo(first);
        assertThat(deviceRepository.findAll()).hasSize(1);
    }
}
