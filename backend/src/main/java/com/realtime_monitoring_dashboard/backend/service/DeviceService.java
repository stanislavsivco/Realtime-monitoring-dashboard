package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int OFFLINE_THRESHOLD_SECONDS = 30;

    private final DeviceRepository deviceRepository;
    private final MetricRepository metricRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<DeviceDTO> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .sorted((a, b) -> statusPriority(a.getStatus()) - statusPriority(b.getStatus()))
                .toList();
    }

    public DeviceDTO getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));
        return mapToDTO(device);
    }

    public DeviceDTO createDevice(CreateDeviceRequestDTO request) {
        Device device = Device.builder()
                .name(request.getName())
                .type(request.getType())
                .location(request.getLocation())
                .status(request.getStatus())
                .build();

        Device saved = deviceRepository.save(device);
        DeviceDTO dto = mapToDTO(saved);

        
        messagingTemplate.convertAndSend("/topic/devices", dto);

        return dto;
    }

    public DeviceDTO updateDevice(Long id, CreateDeviceRequestDTO request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        device.setName(request.getName());
        device.setType(request.getType());
        device.setLocation(request.getLocation());
        device.setStatus(request.getStatus());

        Device saved = deviceRepository.save(device);
        DeviceDTO dto = mapToDTO(saved);

        
        messagingTemplate.convertAndSend("/topic/devices", dto);

        return dto;
    }

    public void deleteDevice(Long id) {
        deviceRepository.deleteById(id);

        
        messagingTemplate.convertAndSend("/topic/devices/delete", id);
    }

    private DeviceStatus calculateStatus(Long deviceId) {
        Optional<Metric> latestMetric = metricRepository.findFirstByDeviceIdOrderByTimestampDesc(deviceId);

        if (latestMetric.isEmpty() || latestMetric.get().getTimestamp().isBefore(LocalDateTime.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS))) {
            return DeviceStatus.OFFLINE;
        }

        Metric metric = latestMetric.get();

        if (isCritical(metric)) {
            return DeviceStatus.CRITICAL;
        }

        if (isWarning(metric)) {
            return DeviceStatus.WARNING;
        }

        return DeviceStatus.ONLINE;
    }

    private boolean isCritical(Metric metric) {
        return (metric.getCpu() != null && metric.getCpu() > 90.0)
            || (metric.getRam() != null && metric.getRam() > 90.0)
            || (metric.getDisk() != null && metric.getDisk() > 90.0)
            || (metric.getLatencyMs() != null && metric.getLatencyMs() > 1000)
            || (metric.getNetworkInMbps() != null && metric.getNetworkInMbps() > 950.0)
            || (metric.getNetworkOutMbps() != null && metric.getNetworkOutMbps() > 950.0);
    }

    private boolean isWarning(Metric metric) {
        return (metric.getCpu() != null && metric.getCpu() > 75.0)
            || (metric.getRam() != null && metric.getRam() > 75.0)
            || (metric.getDisk() != null && metric.getDisk() > 75.0)
            || (metric.getLatencyMs() != null && metric.getLatencyMs() > 500)
            || (metric.getNetworkInMbps() != null && metric.getNetworkInMbps() > 800.0)
            || (metric.getNetworkOutMbps() != null && metric.getNetworkOutMbps() > 800.0);
    }

    private DeviceDTO mapToDTO(Device device) {
        return DeviceDTO.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .location(device.getLocation())
                .status(calculateStatus(device.getId()))
                .build();
    }

    private int statusPriority(DeviceStatus status) {
        return switch (status) {
            case CRITICAL -> 0;
            case OFFLINE -> 1;
            case WARNING -> 2;
            case ONLINE -> 3;
        };
    }
}