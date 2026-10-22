package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MetricRetentionIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);

    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Device device;

    private Metric metricAt(LocalDateTime timestamp) {
        return metricRepository.save(Metric.builder()
                .device(device).timestamp(timestamp).cpu(10.0).ram(20.0).disk(30.0).build());
    }

    private void saveDevice() {
        device = deviceRepository.save(Device.builder()
                .name("Retention-Server").type("Server").location("DC1").status(DeviceStatus.ONLINE).build());
    }

    @Test
    void deletesOnlyMetricsOlderThanRetention() {
        saveDevice();
        Metric old = metricAt(NOW.minusDays(8));
        Metric justExpired = metricAt(NOW.minusDays(7).minusSeconds(1));
        Metric exactlyAtCutoff = metricAt(NOW.minusDays(7));
        Metric recent = metricAt(NOW.minusHours(1));
        entityManager.flush();
        entityManager.clear();

        int deleted = new MetricRetentionService(metricRepository, 7).deleteMetricsOlderThanRetention(NOW);

        assertThat(deleted).isEqualTo(2);
        assertThat(metricRepository.findAll()).extracting(Metric::getId)
                .containsExactlyInAnyOrder(exactlyAtCutoff.getId(), recent.getId())
                .doesNotContain(old.getId(), justExpired.getId());
        assertThat(deviceRepository.existsById(device.getId())).isTrue();
    }

    @Test
    void deletesMoreThanOneBatch() {
        saveDevice();
        int count = MetricRetentionService.BATCH_SIZE + 5;
        for (int i = 0; i < count; i++) {
            metricAt(NOW.minusDays(30).plusMinutes(i));
        }
        Metric recent = metricAt(NOW);
        entityManager.flush();
        entityManager.clear();

        int deleted = new MetricRetentionService(metricRepository, 7).deleteMetricsOlderThanRetention(NOW);

        assertThat(deleted).isEqualTo(count);
        assertThat(metricRepository.findAll()).extracting(Metric::getId).containsExactly(recent.getId());
    }
}
