package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
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

    private static final int OFFLINE_THRESHOLD_SECONDS = 30;

    private final MetricRepository metricRepository;
    private final DeviceRepository deviceRepository;
    private final AlertService alertService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DeviceStatusCalculator statusCalculator;

    @Transactional
    public MetricDTO saveMetric(MetricDTO dto) {
        Device device = deviceRepository.findById(dto.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Device not found with id: " + dto.getDeviceId()));

        Metric metric = Metric.builder()
                .device(device)
                .cpu(dto.getCpu())
                .ram(dto.getRam())
                .disk(dto.getDisk())
                .latencyMs(dto.getLatencyMs())
                .networkInMbps(dto.getNetworkInMbps())
                .networkOutMbps(dto.getNetworkOutMbps())
                .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now())
                .build();

        applyStatusAndAlerts(device, metric);

        Metric savedMetric = metricRepository.save(metric);
        MetricDTO savedDto = mapToDTO(savedMetric);

        messagingTemplate.convertAndSend("/topic/metrics", savedDto);

        return savedDto;
    }

    private void applyStatusAndAlerts(Device device, Metric metric) {
    DeviceStatus newStatus = statusCalculator.calculate(metric);
    DeviceStatus previousStatus = device.getStatus();

    if (newStatus != previousStatus) {
        alertService.resolveActiveAlertsForDevice(device);
    }

    if (newStatus == DeviceStatus.CRITICAL) {
        alertService.createAlert(device, AlertSeverity.CRITICAL, buildAlertMessage(device, metric));
    } else if (newStatus == DeviceStatus.WARNING) {
        alertService.createAlert(device, AlertSeverity.WARNING, buildAlertMessage(device, metric));
    }

    device.setStatus(newStatus);
    deviceRepository.save(device);
}

    private String buildAlertMessage(Device device, Metric metric) {
        return String.format(
                "Load on %s: CPU %.1f%%, RAM %.1f%%, Disk %.1f%%, Latency %d ms",
                device.getName(),
                metric.getCpu() != null ? metric.getCpu() : 0.0,
                metric.getRam() != null ? metric.getRam() : 0.0,
                metric.getDisk() != null ? metric.getDisk() : 0.0,
                metric.getLatencyMs() != null ? metric.getLatencyMs() : 0);
    }

    @Scheduled(fixedRate = 10000)
    public void checkOfflineDevices() {
        List<Device> devices = deviceRepository.findAll();
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS);

        for (Device device : devices) {
            if (device.getStatus() == DeviceStatus.OFFLINE) {
                continue;
            }

            Optional<Metric> latest = metricRepository.findTopByDeviceIdOrderByTimestampDesc(device.getId());
            boolean isStale = latest.isEmpty() || latest.get().getTimestamp().isBefore(threshold);

            if (isStale) {
                device.setStatus(DeviceStatus.OFFLINE);
                deviceRepository.save(device);
                messagingTemplate.convertAndSend("/topic/devices", mapDeviceToDTO(device));
            }
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Device with ID " + deviceId + " does not have any metrics or does not exist"));
        return mapToDTO(metric);
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

    public MetricSummaryDTO getMetricSummary(Long deviceId, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusDays(7);
        if (endDate == null) endDate = LocalDateTime.now();

        return metricRepository.getMetricSummary(deviceId, startDate, endDate);
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

    private DeviceDTO mapDeviceToDTO(Device device) {
        return DeviceDTO.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .location(device.getLocation())
                .status(device.getStatus())
                .build();
    }
}