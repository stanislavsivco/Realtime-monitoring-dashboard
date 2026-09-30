package com.realtime_monitoring_dashboard.backend.service;

import org.springframework.stereotype.Component;

import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;

@Component
public class DeviceStatusCalculator {

    private static final double CPU_CRITICAL = 90.0;
    private static final double CPU_WARNING = 75.0;

    private static final double RAM_CRITICAL = 90.0;
    private static final double RAM_WARNING = 75.0;

    private static final double DISK_CRITICAL = 90.0;
    private static final double DISK_WARNING = 75.0;

    private static final int LATENCY_CRITICAL_MS = 500;
    private static final int LATENCY_WARNING_MS = 200;

    private static final double NETWORK_CRITICAL_MBPS = 950.0;
    private static final double NETWORK_WARNING_MBPS = 800.0;

    public DeviceStatus calculate(Metric metric) {
        if (isCritical(metric)) {
            return DeviceStatus.CRITICAL;
        }
        if (isWarning(metric)) {
            return DeviceStatus.WARNING;
        }
        return DeviceStatus.ONLINE;
    }

    private boolean isCritical(Metric metric) {
        return exceeds(metric.getCpu(), CPU_CRITICAL)
                || exceeds(metric.getRam(), RAM_CRITICAL)
                || exceeds(metric.getDisk(), DISK_CRITICAL)
                || exceedsInt(metric.getLatencyMs(), LATENCY_CRITICAL_MS)
                || exceeds(metric.getNetworkInMbps(), NETWORK_CRITICAL_MBPS)
                || exceeds(metric.getNetworkOutMbps(), NETWORK_CRITICAL_MBPS);
    }

    private boolean isWarning(Metric metric) {
        return exceeds(metric.getCpu(), CPU_WARNING)
                || exceeds(metric.getRam(), RAM_WARNING)
                || exceeds(metric.getDisk(), DISK_WARNING)
                || exceedsInt(metric.getLatencyMs(), LATENCY_WARNING_MS)
                || exceeds(metric.getNetworkInMbps(), NETWORK_WARNING_MBPS)
                || exceeds(metric.getNetworkOutMbps(), NETWORK_WARNING_MBPS);
    }

    private boolean exceeds(Double value, double threshold) {
        return value != null && value > threshold;
    }

    private boolean exceedsInt(Integer value, int threshold) {
        return value != null && value > threshold;
    }
}