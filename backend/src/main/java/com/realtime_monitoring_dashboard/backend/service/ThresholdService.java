package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.dto.ThresholdDTO;
import com.realtime_monitoring_dashboard.backend.dto.UpdateThresholdRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.Threshold;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.ThresholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ThresholdService {

    private static final double DEFAULT_CPU_WARNING = 75.0;
    private static final double DEFAULT_CPU_CRITICAL = 90.0;
    private static final double DEFAULT_RAM_WARNING = 75.0;
    private static final double DEFAULT_RAM_CRITICAL = 90.0;
    private static final double DEFAULT_DISK_WARNING = 75.0;
    private static final double DEFAULT_DISK_CRITICAL = 90.0;
    private static final int DEFAULT_LATENCY_WARNING_MS = 200;
    private static final int DEFAULT_LATENCY_CRITICAL_MS = 500;

    private final ThresholdRepository thresholdRepository;
    private final DeviceRepository deviceRepository;

    public Threshold getOrCreateDefaultForDevice(Long deviceId) {
        return thresholdRepository.findByDeviceId(deviceId)
                .orElseGet(() -> createDefaultThreshold(deviceId));
    }

    public ThresholdDTO getThresholdByDeviceId(Long deviceId) {
        return mapToDTO(getOrCreateDefaultForDevice(deviceId));
    }

    public ThresholdDTO updateThreshold(Long deviceId, UpdateThresholdRequestDTO request) {
        Threshold threshold = getOrCreateDefaultForDevice(deviceId);

        threshold.setCpuWarning(request.getCpuWarning());
        threshold.setCpuCritical(request.getCpuCritical());
        threshold.setRamWarning(request.getRamWarning());
        threshold.setRamCritical(request.getRamCritical());
        threshold.setDiskWarning(request.getDiskWarning());
        threshold.setDiskCritical(request.getDiskCritical());
        threshold.setLatencyWarningMs(request.getLatencyWarningMs());
        threshold.setLatencyCriticalMs(request.getLatencyCriticalMs());

        Threshold saved = thresholdRepository.save(threshold);
        return mapToDTO(saved);
    }

    private Threshold createDefaultThreshold(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device with ID " + deviceId + " does not exist"));

        Threshold defaultThreshold = Threshold.builder()
                .device(device)
                .cpuWarning(DEFAULT_CPU_WARNING)
                .cpuCritical(DEFAULT_CPU_CRITICAL)
                .ramWarning(DEFAULT_RAM_WARNING)
                .ramCritical(DEFAULT_RAM_CRITICAL)
                .diskWarning(DEFAULT_DISK_WARNING)
                .diskCritical(DEFAULT_DISK_CRITICAL)
                .latencyWarningMs(DEFAULT_LATENCY_WARNING_MS)
                .latencyCriticalMs(DEFAULT_LATENCY_CRITICAL_MS)
                .build();

        return thresholdRepository.save(defaultThreshold);
    }

    private ThresholdDTO mapToDTO(Threshold threshold) {
        return ThresholdDTO.builder()
                .id(threshold.getId())
                .deviceId(threshold.getDevice().getId())
                .cpuWarning(threshold.getCpuWarning())
                .cpuCritical(threshold.getCpuCritical())
                .ramWarning(threshold.getRamWarning())
                .ramCritical(threshold.getRamCritical())
                .diskWarning(threshold.getDiskWarning())
                .diskCritical(threshold.getDiskCritical())
                .latencyWarningMs(threshold.getLatencyWarningMs())
                .latencyCriticalMs(threshold.getLatencyCriticalMs())
                .build();
    }
}