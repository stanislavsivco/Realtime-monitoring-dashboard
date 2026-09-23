package com.realtime_monitoring_dashboard.backend.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final DeviceRepository deviceRepository;
    private final MetricRepository metricRepository;

    private final Random random = new Random();

    private static final int METRICS_PER_DEVICE = 24;
    private static final int MINUTES_BETWEEN_METRICS = 5;

    @Override
    public void run(String... args) {
        if (deviceRepository.count() > 0) {
            log.info("Database already contains devices ({}), seeding continues.",
                    deviceRepository.count());
            return;
        }

        log.info("Database is empty – generating test devices and metrics...");

        List<Device> seedDevices = List.of(
                Device.builder()
                        .name("Web Server 01")
                        .type("Server")
                        .location("Bratislava - DC1")
                        .status(DeviceStatus.ONLINE)
                        .build(),
                Device.builder()
                        .name("DB Server - Primary")
                        .type("Database")
                        .location("Bratislava - DC3")
                        .status(DeviceStatus.WARNING)
                        .build(),
                Device.builder()
                        .name("Edge Router 3")
                        .type("Router")
                        .location("Košice")
                        .status(DeviceStatus.ONLINE)
                        .build(),
                Device.builder()
                        .name("Backup NAS")
                        .type("Storage")
                        .location("Bratislava - DC2")
                        .status(DeviceStatus.OFFLINE)
                        .build()
        );

        List<Device> savedDevices = deviceRepository.saveAll(seedDevices);

        for (Device device : savedDevices) {
            generateMetricHistory(device);
        }

        log.info("Seeding complete: {} devices, {} metrics for device.",
                savedDevices.size(), METRICS_PER_DEVICE);
    }

    private void generateMetricHistory(Device device) {
        LocalDateTime now = LocalDateTime.now();

        int recordsToGenerate = METRICS_PER_DEVICE;
        int offsetStartMinutes = 0;

        if (device.getStatus() == DeviceStatus.OFFLINE) {
            offsetStartMinutes = 60;
            recordsToGenerate = METRICS_PER_DEVICE / 2;
        }

        for (int i = recordsToGenerate; i >= 0; i--) {
            LocalDateTime timestamp = now.minusMinutes(offsetStartMinutes + (long) i * MINUTES_BETWEEN_METRICS);

            double disk = round2(randomInRange(diskRangeFor(device.getStatus())));
            double ram = round2(randomInRange(ramRangeFor(device.getStatus())));
            double cpu = round2(randomInRange(cpuRangeFor(device.getStatus())));
            int latency = (int) Math.round(randomInRange(latencyRangeFor(device.getStatus())));
            double networkIn = round2(random.nextDouble() * 500.0);
            double networkOut = round2(random.nextDouble() * 500.0);

            Metric metric = Metric.builder()
                    .device(device)
                    .timestamp(timestamp)
                    .disk(disk)
                    .ram(ram)
                    .cpu(cpu)
                    .latencyMs(latency)
                    .networkInMbps(networkIn)
                    .networkOutMbps(networkOut)
                    .build();

            metricRepository.save(metric);
        }
    }

    private double randomInRange(double[] range) {
        return range[0] + (range[1] - range[0]) * random.nextDouble();
    }

    private double[] diskRangeFor(DeviceStatus status) {
        return switch (status) {
            case CRITICAL -> new double[]{90.0, 98.0};
            case WARNING -> new double[]{75.0, 89.0};
            default -> new double[]{15.0, 60.0};
        };
    }

    private double[] ramRangeFor(DeviceStatus status) {
        return switch (status) {
            case CRITICAL -> new double[]{90.0, 98.0};
            case WARNING -> new double[]{80.0, 89.0};
            default -> new double[]{25.0, 65.0};
        };
    }

    private double[] cpuRangeFor(DeviceStatus status) {
        return switch (status) {
            case CRITICAL -> new double[]{85.0, 99.0};
            case WARNING -> new double[]{70.0, 88.0};
            default -> new double[]{10.0, 55.0};
        };
    }

    private double[] latencyRangeFor(DeviceStatus status) {
        return switch (status) {
            case CRITICAL -> new double[]{200.0, 350.0};
            case WARNING -> new double[]{100.0, 199.0};
            default -> new double[]{5.0, 60.0};
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}