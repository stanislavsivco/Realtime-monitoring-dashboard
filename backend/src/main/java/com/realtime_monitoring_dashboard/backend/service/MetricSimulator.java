package com.realtime_monitoring_dashboard.backend.service;

import java.util.Random;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.realtime_monitoring_dashboard.backend.dto.IngestMetricRequestDTO;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MetricSimulator {

    private final DeviceRepository deviceRepository;
    private final MetricService metricService;
    private final Random random = new Random();

    @Scheduled(fixedRate = 10000)
    public void generateMetrics() {
        for (Device device : deviceRepository.findBySimulatedTrue()) {
            metricService.saveMetric(IngestMetricRequestDTO.builder()
                    .deviceId(device.getId())
                    .cpu(round2(10 + random.nextDouble() * 85))
                    .ram(round2(20 + random.nextDouble() * 75))
                    .disk(round2(10 + random.nextDouble() * 85))
                    .latencyMs(5 + random.nextInt(146))
                    .networkInMbps(round2(random.nextDouble() * 500))
                    .networkOutMbps(round2(random.nextDouble() * 500))
                    .build());
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
