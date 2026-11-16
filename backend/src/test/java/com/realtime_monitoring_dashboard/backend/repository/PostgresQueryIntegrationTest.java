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
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        saveAlert(device, severity, resolved, BASE);
    }

    private void saveAlert(Device device, AlertSeverity severity, boolean resolved, LocalDateTime timestamp) {
        alertRepository.save(Alert.builder()
                .device(device).severity(severity).message("test").timestamp(timestamp).resolved(resolved).build());
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

    private void saveFiveMetricsOneMinuteApart(Device device) {
        for (int i = 0; i < 5; i++) {
            saveMetric(device, BASE.plusMinutes(i), 10.0 + i, 100 + i);
        }
        entityManager.flush();
        entityManager.clear();
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
    void alertFilterPagesWithTotalsAndSortOrder() {
        Device device = saveDevice("alert-paging");
        for (int i = 0; i < 5; i++) {
            saveAlert(device, AlertSeverity.CRITICAL, false, BASE.plusMinutes(i));
        }
        saveAlert(device, AlertSeverity.WARNING, false, BASE);
        saveAlert(device, AlertSeverity.CRITICAL, true, BASE);
        entityManager.flush();
        entityManager.clear();

        Sort ascending = Sort.by(Sort.Direction.ASC, "timestamp");
        Page<Alert> second = alertRepository.findAlertsFiltered(
                false, AlertSeverity.CRITICAL, PageRequest.of(1, 2, ascending));
        Page<Alert> last = alertRepository.findAlertsFiltered(
                false, AlertSeverity.CRITICAL, PageRequest.of(2, 2, ascending));

        assertThat(second.getTotalElements()).isEqualTo(5);
        assertThat(second.getTotalPages()).isEqualTo(3);
        assertThat(second.getContent()).extracting(Alert::getTimestamp)
                .containsExactly(BASE.plusMinutes(2), BASE.plusMinutes(3));
        assertThat(last.getContent()).extracting(Alert::getTimestamp).containsExactly(BASE.plusMinutes(4));
        assertThat(last.isLast()).isTrue();
    }

    @Test
    void alertFilterSortsNewestFirstWithoutFilters() {
        Device device = saveDevice("alert-sort");
        saveAlert(device, AlertSeverity.WARNING, false, BASE);
        saveAlert(device, AlertSeverity.CRITICAL, true, BASE.plusHours(2));
        saveAlert(device, AlertSeverity.WARNING, true, BASE.plusHours(1));
        entityManager.flush();
        entityManager.clear();

        Page<Alert> page = alertRepository.findAlertsFiltered(
                null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Alert::getTimestamp)
                .containsExactly(BASE.plusHours(2), BASE.plusHours(1), BASE);
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
    void metricSummaryIgnoresMetricsOfOtherDevices() {
        Device device = saveDevice("summary-own");
        Device other = saveDevice("summary-other");
        saveMetric(device, BASE, 10.0, 100);
        saveMetric(other, BASE, 80.0, 800);
        entityManager.flush();
        entityManager.clear();

        MetricSummaryDTO summary = metricRepository.getMetricSummary(device.getId(), BASE, BASE.plusHours(1));

        assertThat(summary.getAvgCpu()).isEqualTo(10.0);
        assertThat(summary.getMaxCpu()).isEqualTo(10.0);
        assertThat(summary.getAvgLatencyMs()).isEqualTo(100.0);
    }

    @Test
    void metricSummaryOfAnIntervalWithoutMetricsHasNullAggregates() {
        Device device = saveDevice("summary-empty");
        saveMetric(device, BASE, 10.0, 100);
        entityManager.flush();
        entityManager.clear();

        MetricSummaryDTO summary = metricRepository.getMetricSummary(
                device.getId(), BASE.plusDays(1), BASE.plusDays(2));

        assertThat(summary).isNotNull();
        assertThat(summary.getAvgCpu()).isNull();
        assertThat(summary.getMaxCpu()).isNull();
        assertThat(summary.getMinCpu()).isNull();
        assertThat(summary.getAvgLatencyMs()).isNull();
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
    void metricHistoryPagesNewestFirstWithTotals() {
        Device device = saveDevice("history-paging");
        Device other = saveDevice("history-other");
        saveFiveMetricsOneMinuteApart(device);
        saveMetric(other, BASE, 99.0, 99);
        entityManager.flush();
        entityManager.clear();

        Page<Metric> page = metricRepository.findByDeviceId(
                device.getId(), PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "timestamp")));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Metric::getTimestamp)
                .containsExactly(BASE.plusMinutes(2), BASE.plusMinutes(1));
    }

    @Test
    void metricHistoryFromStartDateIncludesTheBound() {
        Device device = saveDevice("history-from");
        saveFiveMetricsOneMinuteApart(device);

        Page<Metric> page = metricRepository.findByDeviceIdAndTimestampGreaterThanEqual(
                device.getId(), BASE.plusMinutes(3), PageRequest.of(0, 10, Sort.by("timestamp")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Metric::getTimestamp)
                .containsExactly(BASE.plusMinutes(3), BASE.plusMinutes(4));
    }

    @Test
    void metricHistoryUpToEndDateIncludesTheBound() {
        Device device = saveDevice("history-to");
        saveFiveMetricsOneMinuteApart(device);

        Page<Metric> page = metricRepository.findByDeviceIdAndTimestampLessThanEqual(
                device.getId(), BASE.plusMinutes(1), PageRequest.of(0, 10, Sort.by("timestamp")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Metric::getTimestamp)
                .containsExactly(BASE, BASE.plusMinutes(1));
    }

    @Test
    void latestMetricIsTheNewestOneOfTheDevice() {
        Device device = saveDevice("latest");
        Device other = saveDevice("latest-other");
        saveFiveMetricsOneMinuteApart(device);
        saveMetric(other, BASE.plusDays(1), 99.0, 99);
        entityManager.flush();
        entityManager.clear();

        Metric latest = metricRepository.findTopByDeviceIdOrderByTimestampDesc(device.getId()).orElseThrow();

        assertThat(latest.getTimestamp()).isEqualTo(BASE.plusMinutes(4));
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

    @Test
    void databaseAllowsUsernamesDifferingOnlyInCaseAndCaseInsensitiveLookupThenFails() {
        userRepository.saveAndFlush(User.builder().username("Case.User").password("x").role(Role.VIEWER).build());
        userRepository.saveAndFlush(User.builder().username("case.user").password("y").role(Role.VIEWER).build());
        entityManager.clear();

        assertThat(userRepository.findAll()).extracting(User::getUsername)
                .contains("Case.User", "case.user");
        assertThrows(IncorrectResultSizeDataAccessException.class,
                () -> userRepository.findByUsernameIgnoreCase("CASE.USER"));
    }
}