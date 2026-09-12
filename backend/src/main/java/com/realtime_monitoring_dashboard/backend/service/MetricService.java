package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 

public class MetricService {

    private final MetricRepository metricRepository;
    private final DeviceRepository deviceRepository;
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

    public Metric generateAndSaveSimulatedMetric() {

        Device device = deviceRepository.findAll().get(0);

        double randomDisk = 10.0 + (85.0 * random.nextDouble());
        double roundedDisk = Math.round(randomDisk * 100.0) / 100.0;

        Metric metric = new Metric();
        metric.setDevice(device);
        metric.setDisk(roundedDisk);
        metric.setTimestamp(LocalDateTime.now());


        return metricRepository.save(metric);
    }

    @Scheduled(fixedRate = 5000)
    public void autoGenerateMetrics() {
        Metric metric = generateAndSaveSimulatedMetric();
        System.out.println("Automaticky uložená metrika: " + metric.getDisk() + "% o" + metric.getTimestamp());

    }

    public List<Metric> getMetricsByDeviceId(Long deviceId) {
    return metricRepository.findByDeviceId(deviceId);
}

}