package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MetricService {

    private final MetricRepository metricRepository;
    private final DeviceRepository deviceRepository;
    private final Random random = new Random();

    public List<Metric> getAllMetrics() {
        return metricRepository.findAll();
    }

    public Metric saveMetric(Metric metric) {
        if (metric.getTimestamp() == null) {
            metric.setTimestamp(LocalDateTime.now());
        }
        return metricRepository.save(metric);
    }

    @Scheduled(fixedRate = 5000)
    public void autoGenerateMetrics() {
        List<Device> devices = deviceRepository.findAll();

        if (devices.isEmpty()) {
            System.out.println("No devices found in database, skipping metric generation.");
            return;
        }

        for (Device device : devices) {
            double randomDisk = 10.0 + (85.0 * random.nextDouble());
            double roundedDisk = Math.round(randomDisk * 100.0) / 100.0;

            int randomLatency = 5 + random.nextInt(145);

            Metric metric = new Metric();
            metric.setDevice(device);
            metric.setDisk(roundedDisk);
            metric.setLatencyMs(randomLatency);
            metric.setTimestamp(LocalDateTime.now());

            DeviceStatus newStatus = calculateStatus(roundedDisk, randomLatency);
            device.setStatus(newStatus);
            deviceRepository.save(device);

            metricRepository.save(metric);
            System.out.println("Metric for " + device.getName() + ": Disk " + roundedDisk + "%, Latency " + randomLatency + " ms");
        }
    }

    public List<MetricDTO> getMetricsByDeviceId(Long deviceId) {
        return metricRepository.findTop100ByDeviceIdOrderByTimestampAsc(deviceId)
                .stream()
                .map(metric -> MetricDTO.builder()
                        .id(metric.getId())
                        .deviceId(metric.getDevice().getId())
                        .timestamp(metric.getTimestamp())
                        .disk(metric.getDisk())
                        .latencyMs(metric.getLatencyMs()) // <-- Pridané pre posielanie latencie
                        .build())
                .toList();
    }

    public MetricDTO getLatestMetricByDeviceId(Long deviceId) { // <-- Pridaná chýbajúca metóda
        Metric metric = metricRepository.findTopByDeviceIdOrderByTimestampDesc(deviceId)
                .orElseThrow(() -> new RuntimeException("No metrics found for device " + deviceId));

        return MetricDTO.builder()
                .id(metric.getId())
                .deviceId(metric.getDevice().getId())
                .timestamp(metric.getTimestamp())
                .disk(metric.getDisk())
                .latencyMs(metric.getLatencyMs())
                .build();
    }

    private DeviceStatus calculateStatus(double disk, int latencyMs) {
        if (disk >= 90.0 || latencyMs >= 200) {
            return DeviceStatus.CRITICAL;
        } else if (disk >= 75.0 || latencyMs >= 100) {
            return DeviceStatus.WARNING;
        } else {
            return DeviceStatus.ONLINE;
        }
    }
}