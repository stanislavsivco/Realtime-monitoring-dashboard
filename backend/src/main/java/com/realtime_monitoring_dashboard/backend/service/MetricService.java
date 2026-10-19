package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.IngestMetricRequestDTO;
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
    private static final int DEFAULT_SUMMARY_DAYS = 7;

    private final MetricRepository metricRepository;
    private final DeviceRepository deviceRepository;
    private final AlertService alertService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DeviceStatusCalculator statusCalculator;
    private final Random random = new Random();

    @Transactional
    public MetricDTO saveMetric(IngestMetricRequestDTO request) {
        Device device = deviceRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Device not found with id: " + request.getDeviceId()));
        return persistMetric(device, request);
    }

    @Scheduled(fixedRate = 10000)
    public void autoGenerateMetrics() {
        for (Device device : deviceRepository.findBySimulatedTrue()) {
            IngestMetricRequestDTO request = IngestMetricRequestDTO.builder()
                    .deviceId(device.getId())
                    .cpu(round2(10 + random.nextDouble() * 85))
                    .ram(round2(20 + random.nextDouble() * 75))
                    .disk(round2(10 + random.nextDouble() * 85))
                    .latencyMs(5 + random.nextInt(146))
                    .networkInMbps(round2(random.nextDouble() * 500))
                    .networkOutMbps(round2(random.nextDouble() * 500))
                    .build();
            saveMetric(request);
        }
    }

    private MetricDTO persistMetric(Device device, IngestMetricRequestDTO request) {
        Metric metric = Metric.builder()
                .device(device)
                .cpu(request.getCpu())
                .ram(request.getRam())
                .disk(request.getDisk())
                .latencyMs(request.getLatencyMs())
                .networkInMbps(request.getNetworkInMbps())
                .networkOutMbps(request.getNetworkOutMbps())
                .timestamp(LocalDateTime.now())
                .build();

        applyStatusAndAlerts(device, metric);

        Metric savedMetric = metricRepository.save(metric);
        MetricDTO savedDto = mapToDTO(savedMetric);

        messagingTemplate.convertAndSend("/topic/metrics", savedDto);

        return savedDto;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void applyStatusAndAlerts(Device device, Metric metric) {
        DeviceStatus newStatus = statusCalculator.calculate(device, metric);
        DeviceStatus previousStatus = device.getStatus();
        boolean statusChanged = newStatus != previousStatus;

        if (statusChanged) {
            alertService.resolveActiveAlertsForDevice(device);
        }

        if (newStatus == DeviceStatus.CRITICAL) {
            alertService.createAlert(device, AlertSeverity.CRITICAL, buildAlertMessage(device, metric));
        } else if (newStatus == DeviceStatus.WARNING) {
            alertService.createAlert(device, AlertSeverity.WARNING, buildAlertMessage(device, metric));
        }

        device.setStatus(newStatus);
        Device savedDevice = deviceRepository.save(device);

        if (statusChanged) {
            messagingTemplate.convertAndSend("/topic/devices", mapDeviceToDTO(savedDevice));
        }
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

        if (startDate != null && endDate != null) {
            validateInterval(startDate, endDate);
            return metricRepository.findByDeviceIdAndTimestampBetween(deviceId, startDate, endDate, pageable)
                    .map(this::mapToDTO);
        }
        return metricRepository.findByDeviceId(deviceId, pageable).map(this::mapToDTO);
    }

    public MetricSummaryDTO getMetricSummary(Long deviceId, LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();
        LocalDateTime start = startDate != null ? startDate : end.minusDays(DEFAULT_SUMMARY_DAYS);
        validateInterval(start, end);

        return metricRepository.getMetricSummary(deviceId, start, end);
    }

    private void validateInterval(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
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
                .simulated(device.isSimulated())
                .build();
    }
}
