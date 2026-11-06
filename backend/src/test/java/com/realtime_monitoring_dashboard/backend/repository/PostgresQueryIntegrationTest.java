package com.realtime_monitoring_dashboard.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.Threshold;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.support.PostgresTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresQueryIntegrationTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 21, 12, 0);

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private ThresholdRepository thresholdRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Device saveDevice(String name) {
        return deviceRepository.save(Device.builder()
                .name(name).type("Server").location("DC1").status(DeviceStatus.ONLINE).build());
    }

    private void saveAlert(Device device, AlertSeverity severity, boolean resolved) {
        alertRepository.save(Alert.builder()
                .device(device).severity(severity).message("test").timestamp(BASE).resolved(resolved).build());
    }

    private void saveMetric(Device device, LocalDateTime timestamp, double cpu, int latencyMs) {
        metricRepository.save(Metric.builder()
                .device(device).timestamp(timestamp).cpu(cpu).ram(20.0).disk(30.0).latencyMs(latencyMs).build());
    }

    private List<Alert> alerts(Boolean resolved, AlertSeverity severity) {
        return alertRepository.findAlertsFiltered(resolved, severity, PageRequest.of(0, 10)).getContent();
    }

    private static Threshold thresholdFor(Device device) {
        return Threshold.builder().device(device).cpuWarning(70.0).cpuCritical(90.0).ramWarning(70.0)
                .ramCritical(90.0).diskWarning(70.0).diskCritical(90.0).latencyWarningMs(100)
                .latencyCriticalMs(200).build();
    }

    @Test
    void alertFilterAcceptsNullAndNonNullParameters() {
        Device device = saveDevice("alert-filter");
        saveAlert(device, AlertSeverity.CRITICAL, false);
        saveAlert(device, AlertSeverity.WARNING, false);
        saveAlert(device, AlertSeverity.CRITICAL, true);
        entityManager.flush();
        entityManager.clear();

        assertThat(alerts(null, null)).hasSize(3);
        assertThat(alerts(false, null)).hasSize(2);
        assertThat(alerts(null, AlertSeverity.CRITICAL)).hasSize(2);
        assertThat(alerts(false, AlertSeverity.CRITICAL)).hasSize(1);
    }

    @Test
    void metricSummaryAggregatesOnlyTheRequestedInterval() {
        Device device = saveDevice("summary");
        saveMetric(device, BASE, 10.0, 100);
        saveMetric(device, BASE.plusHours(1), 20.0, 200);
        saveMetric(device, BASE.plusHours(2), 90.0, 900);
        entityManager.flush();
        entityManager.clear();

        MetricSummaryDTO summary = metricRepository.getMetricSummary(device.getId(), BASE, BASE.plusHours(1));

        assertThat(summary.getAvgCpu()).isEqualTo(15.0);
        assertThat(summary.getMaxCpu()).isEqualTo(20.0);
        assertThat(summary.getMinCpu()).isEqualTo(10.0);
        assertThat(summary.getAvgLatencyMs()).isEqualTo(150.0);
    }

    @Test
    void metricHistoryIntervalIncludesBothBounds() {
        Device device = saveDevice("history");
        saveMetric(device, BASE.minusSeconds(1), 1.0, 1);
        saveMetric(device, BASE, 2.0, 2);
        saveMetric(device, BASE.plusHours(1), 3.0, 3);
        saveMetric(device, BASE.plusHours(1).plusSeconds(1), 4.0, 4);
        entityManager.flush();
        entityManager.clear();

        List<Metric> page = metricRepository.findByDeviceIdAndTimestampBetween(
                device.getId(), BASE, BASE.plusHours(1), PageRequest.of(0, 10)).getContent();

        assertThat(page).extracting(Metric::getCpu).containsExactlyInAnyOrder(2.0, 3.0);
    }

    @Test
    void databaseAllowsOnlyOneThresholdPerDevice() {
        Device device = saveDevice("one-threshold");
        thresholdRepository.saveAndFlush(thresholdFor(device));

        assertThrows(DataIntegrityViolationException.class,
                () -> thresholdRepository.saveAndFlush(thresholdFor(device)));
    }

    @Test
    void databaseRejectsDuplicateUsername() {
        userRepository.saveAndFlush(User.builder().username("dup.user").password("x").role(Role.VIEWER).build());

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(
                User.builder().username("dup.user").password("y").role(Role.VIEWER).build()));
    }
}