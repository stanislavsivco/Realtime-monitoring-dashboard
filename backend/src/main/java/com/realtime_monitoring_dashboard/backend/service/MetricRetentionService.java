package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MetricRetentionService {

    static final int BATCH_SIZE = 1000;

    private final MetricRepository metricRepository;
    private final int retentionDays;

    public MetricRetentionService(MetricRepository metricRepository,
                                  @Value("${metric.retention-days}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalStateException("METRIC_RETENTION_DAYS must be at least 1");
        }
        this.metricRepository = metricRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void deleteExpiredMetrics() {
        int deleted = deleteMetricsOlderThanRetention(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Deleted {} metrics older than {} days.", deleted, retentionDays);
        }
    }

    int deleteMetricsOlderThanRetention(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(retentionDays);
        int total = 0;
        List<Long> ids;

        do {
            ids = metricRepository.findIdsOlderThan(cutoff, PageRequest.of(0, BATCH_SIZE));
            if (!ids.isEmpty()) {
                total += metricRepository.deleteByIdIn(ids);
            }
        } while (ids.size() == BATCH_SIZE);

        return total;
    }
}
