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
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MetricService {

    private final MetricRepository metricRepository;
    private final DeviceRepository deviceRepository;
    private final AlertService alertService;
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

            double randomRam = 20.0 + (75.0 * random.nextDouble());
            double roundedRam = Math.round(randomRam * 100.0) / 100.0;

            int randomLatency = 5 + random.nextInt(145);

            double randomNetworkIn = Math.round((random.nextDouble() * 500.0) * 100.0) / 100.0;
            double randomNetworkOut = Math.round((random.nextDouble() * 500.0) * 100.0) / 100.0;

            Metric metric = new Metric();
            metric.setDevice(device);
            metric.setDisk(roundedDisk);
            metric.setRam(roundedRam);
            metric.setLatencyMs(randomLatency);
            metric.setNetworkInMbps(randomNetworkIn);
            metric.setNetworkOutMbps(randomNetworkOut);
            metric.setTimestamp(LocalDateTime.now());

            DeviceStatus newStatus = calculateStatus(roundedDisk, roundedRam, randomLatency);

            if (newStatus == DeviceStatus.CRITICAL) {
                alertService.createAlert(device, AlertSeverity.CRITICAL, 
                    String.format("Critical load on %s: Disk %.1f%%, RAM %.1f%%, Latency %d ms", 
                        device.getName(), roundedDisk, roundedRam, randomLatency));
            } else if (newStatus == DeviceStatus.WARNING) {
                alertService.createAlert(device, AlertSeverity.WARNING, 
                    String.format("Warning load on %s: Disk %.1f%%, RAM %.1f%%, Latency %d ms", 
                        device.getName(), roundedDisk, roundedRam, randomLatency));
}
            device.setStatus(newStatus);
            deviceRepository.save(device);

            metricRepository.save(metric);
            System.out.println("Metric for " + device.getName() + ": Disk " + roundedDisk + "%, RAM " + roundedRam + "%, Latency " + randomLatency + " ms");
        }
    }

    public List<MetricDTO> getMetricsByDeviceId(Long deviceId) {
        return metricRepository.findTop120ByDeviceIdOrderByTimestampAsc(deviceId)
                .stream()
                .map(metric -> MetricDTO.builder()
                        .id(metric.getId())
                        .deviceId(metric.getDevice().getId())
                        .timestamp(metric.getTimestamp())
                        .disk(metric.getDisk())
                        .ram(metric.getRam())
                        .latencyMs(metric.getLatencyMs())
                        .networkInMbps(metric.getNetworkInMbps())
                        .networkOutMbps(metric.getNetworkOutMbps())
                        .build())
                .toList();
    }

    public MetricDTO getLatestMetricByDeviceId(Long deviceId) {
        Metric metric = metricRepository.findTopByDeviceIdOrderByTimestampDesc(deviceId)
                .orElseThrow(() -> new RuntimeException("No metrics found for device " + deviceId));

        return MetricDTO.builder()
                .id(metric.getId())
                .deviceId(metric.getDevice().getId())
                .timestamp(metric.getTimestamp())
                .disk(metric.getDisk())
                .ram(metric.getRam())
                .latencyMs(metric.getLatencyMs())
                .networkInMbps(metric.getNetworkInMbps())
                .networkOutMbps(metric.getNetworkOutMbps())
                .build();
    }

    private DeviceStatus calculateStatus(double disk, double ram, int latencyMs) {
        if (disk >= 90.0 || ram >= 90.0 || latencyMs >= 200) {
            return DeviceStatus.CRITICAL;
        } else if (disk >= 75.0 || ram >= 80.0 || latencyMs >= 100) {
            return DeviceStatus.WARNING;
        } else {
            return DeviceStatus.ONLINE;
        }
    }
}