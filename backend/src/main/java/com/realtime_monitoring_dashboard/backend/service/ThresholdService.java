package com.realtime_monitoring_dashboard.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtime_monitoring_dashboard.backend.dto.ThresholdDTO;
import com.realtime_monitoring_dashboard.backend.dto.UpdateThresholdRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.InvalidThresholdException;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.Threshold;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.ThresholdRepository;

import lombok.RequiredArgsConstructor;

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

    public Threshold getEffectiveThreshold(Long deviceId) {
        return thresholdRepository.findByDeviceId(deviceId)
                .orElseGet(this::defaultThreshold);
    }

    public ThresholdDTO getThresholdByDeviceId(Long deviceId) {
        Device device = findDevice(deviceId);
        return mapToDTO(getEffectiveThreshold(deviceId), device.getId());
    }

    @Transactional
    public ThresholdDTO updateThreshold(Long deviceId, UpdateThresholdRequestDTO request) {
        validateWarningBelowCritical(request);

        Device device = findDevice(deviceId);
        Threshold threshold = getEffectiveThreshold(deviceId);

        threshold.setDevice(device);
        threshold.setCpuWarning(request.getCpuWarning());
        threshold.setCpuCritical(request.getCpuCritical());
        threshold.setRamWarning(request.getRamWarning());
        threshold.setRamCritical(request.getRamCritical());
        threshold.setDiskWarning(request.getDiskWarning());
        threshold.setDiskCritical(request.getDiskCritical());
        threshold.setLatencyWarningMs(request.getLatencyWarningMs());
        threshold.setLatencyCriticalMs(request.getLatencyCriticalMs());

        Threshold saved = thresholdRepository.save(threshold);
        return mapToDTO(saved, device.getId());
    }

    private Device findDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device with ID " + deviceId + " does not exist"));
    }

    private void validateWarningBelowCritical(UpdateThresholdRequestDTO request) {
        if (request.getCpuWarning() >= request.getCpuCritical()) {
            throw new InvalidThresholdException("cpuWarning must be lower than cpuCritical");
        }
        if (request.getRamWarning() >= request.getRamCritical()) {
            throw new InvalidThresholdException("ramWarning must be lower than ramCritical");
        }
        if (request.getDiskWarning() >= request.getDiskCritical()) {
            throw new InvalidThresholdException("diskWarning must be lower than diskCritical");
        }
        if (request.getLatencyWarningMs() >= request.getLatencyCriticalMs()) {
            throw new InvalidThresholdException("latencyWarningMs must be lower than latencyCriticalMs");
        }
    }

    private Threshold defaultThreshold() {
        return Threshold.builder()
                .cpuWarning(DEFAULT_CPU_WARNING)
                .cpuCritical(DEFAULT_CPU_CRITICAL)
                .ramWarning(DEFAULT_RAM_WARNING)
                .ramCritical(DEFAULT_RAM_CRITICAL)
                .diskWarning(DEFAULT_DISK_WARNING)
                .diskCritical(DEFAULT_DISK_CRITICAL)
                .latencyWarningMs(DEFAULT_LATENCY_WARNING_MS)
                .latencyCriticalMs(DEFAULT_LATENCY_CRITICAL_MS)
                .build();
    }

    private ThresholdDTO mapToDTO(Threshold threshold, Long deviceId) {
        return ThresholdDTO.builder()
                .id(threshold.getId())
                .deviceId(deviceId)
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
