package com.realtime_monitoring_dashboard.backend.service;

import org.springframework.stereotype.Component;

import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.model.Threshold;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DeviceStatusCalculator {

    private final ThresholdService thresholdService;

    public DeviceStatus calculate(Device device, Metric metric) {
        Threshold threshold = thresholdService.getOrCreateDefaultForDevice(device.getId());

        if (isCritical(metric, threshold)) {
            return DeviceStatus.CRITICAL;
        }
        if (isWarning(metric, threshold)) {
            return DeviceStatus.WARNING;
        }
        return DeviceStatus.ONLINE;
    }

    private boolean isCritical(Metric metric, Threshold threshold) {
        return exceeds(metric.getCpu(), threshold.getCpuCritical())
                || exceeds(metric.getRam(), threshold.getRamCritical())
                || exceeds(metric.getDisk(), threshold.getDiskCritical())
                || exceedsInt(metric.getLatencyMs(), threshold.getLatencyCriticalMs());
    }

    private boolean isWarning(Metric metric, Threshold threshold) {
        return exceeds(metric.getCpu(), threshold.getCpuWarning())
                || exceeds(metric.getRam(), threshold.getRamWarning())
                || exceeds(metric.getDisk(), threshold.getDiskWarning())
                || exceedsInt(metric.getLatencyMs(), threshold.getLatencyWarningMs());
    }

    private boolean exceeds(Double value, Double threshold) {
        return value != null && threshold != null && value > threshold;
    }

    private boolean exceedsInt(Integer value, Integer threshold) {
        return value != null && threshold != null && value > threshold;
    }
}