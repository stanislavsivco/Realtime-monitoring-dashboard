package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
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
    private final AlertService alertService;
    private final SimpMessagingTemplate messagingTemplate;
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
        double roundedDisk = round2(10.0 + (85.0 * random.nextDouble()));
        double roundedRam = round2(20.0 + (75.0 * random.nextDouble()));
        double roundedCpu = round2(10.0 + (85.0 * random.nextDouble()));
        int randomLatency = 5 + random.nextInt(145);
        double randomNetworkIn = round2(random.nextDouble() * 500.0);
        double randomNetworkOut = round2(random.nextDouble() * 500.0);

            Metric metric = Metric.builder()
                .device(device)
                .disk(roundedDisk)
                .ram(roundedRam)
                .cpu(roundedCpu)
                .latencyMs(randomLatency)
                .networkInMbps(randomNetworkIn)
                .networkOutMbps(randomNetworkOut)
                .timestamp(LocalDateTime.now())
                .build();

            DeviceStatus newStatus = calculateStatus(metric);

           if (newStatus == DeviceStatus.CRITICAL) {
            alertService.createAlert(device, AlertSeverity.CRITICAL, 
                String.format("Critical load on %s: CPU %.1f%%, RAM %.1f%%, Disk %.1f%%, Latency %d ms", 
                    device.getName(), roundedCpu, roundedRam, roundedDisk, randomLatency));
        } else if (newStatus == DeviceStatus.WARNING) {
            alertService.createAlert(device, AlertSeverity.WARNING, 
                String.format("Warning load on %s: CPU %.1f%%, RAM %.1f%%, Disk %.1f%%, Latency %d ms", 
                    device.getName(), roundedCpu, roundedRam, roundedDisk, randomLatency));
        } else {
            alertService.resolveActiveAlertsForDevice(device);
        }

            device.setStatus(newStatus);
            deviceRepository.save(device);

            Metric savedMetric = metricRepository.save(metric);
            
            
            MetricDTO metricDTO = mapToDTO(savedMetric);
            messagingTemplate.convertAndSend("/topic/metrics", metricDTO);

            System.out.println("Metric for " + device.getName() + ": Disk " + roundedDisk + "%, RAM " + roundedRam + "%, Latency " + randomLatency + " ms");
        }
    }

    public List<MetricDTO> getMetricsByDeviceId(Long deviceId) {
        return metricRepository.findTop120ByDeviceIdOrderByTimestampAsc(deviceId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    public MetricDTO getLatestMetricByDeviceId(Long deviceId) {
        Metric metric = metricRepository.findTopByDeviceIdOrderByTimestampDesc(deviceId)
                .orElseThrow(() -> new RuntimeException("No metrics found for device " + deviceId));

        return mapToDTO(metric);
    }

    private DeviceStatus calculateStatus(Metric metric) {
    if (isCritical(metric)) {
        return DeviceStatus.CRITICAL;
    } else if (isWarning(metric)) {
        return DeviceStatus.WARNING;
    } else {
        return DeviceStatus.ONLINE;
    }
}

private boolean isCritical(Metric metric) {
    return (metric.getCpu() != null && metric.getCpu() > 90.0)
        || (metric.getRam() != null && metric.getRam() > 90.0)
        || (metric.getDisk() != null && metric.getDisk() > 90.0)
        || (metric.getLatencyMs() != null && metric.getLatencyMs() > 500)
        || (metric.getNetworkInMbps() != null && metric.getNetworkInMbps() > 950.0)
        || (metric.getNetworkOutMbps() != null && metric.getNetworkOutMbps() > 950.0);
}

private boolean isWarning(Metric metric) {
    return (metric.getCpu() != null && metric.getCpu() > 75.0)
        || (metric.getRam() != null && metric.getRam() > 75.0)
        || (metric.getDisk() != null && metric.getDisk() > 75.0)
        || (metric.getLatencyMs() != null && metric.getLatencyMs() > 200)
        || (metric.getNetworkInMbps() != null && metric.getNetworkInMbps() > 800.0)
        || (metric.getNetworkOutMbps() != null && metric.getNetworkOutMbps() > 800.0);
}

private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
}

    private MetricDTO mapToDTO(Metric metric) {
        return MetricDTO.builder()
                .id(metric.getId())
                .deviceId(metric.getDevice().getId())
                .timestamp(metric.getTimestamp())
                .disk(metric.getDisk())
                .ram(metric.getRam())
                .cpu(metric.getCpu())
                .latencyMs(metric.getLatencyMs())
                .networkInMbps(metric.getNetworkInMbps())
                .networkOutMbps(metric.getNetworkOutMbps())
                .build();
    }

    public Page<MetricDTO> getMetricsByDeviceIdPaged(
        Long deviceId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable) {

        Page<Metric> metricsPage;

        if (startDate != null && endDate != null) {
            metricsPage = metricRepository.findByDeviceIdAndTimestampBetween(deviceId, startDate, endDate, pageable);
        } else {
            metricsPage = metricRepository.findByDeviceId(deviceId, pageable);
        }

        return metricsPage.map(this::mapToDTO);
        }
    }