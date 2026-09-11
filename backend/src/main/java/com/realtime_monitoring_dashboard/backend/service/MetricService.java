package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 

public class MetricService {

    private final MetricRepository metricRepository;
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
        double randomDisk = 10.0 + (85.0 * random.nextDouble());
        double roundedDisk = Math.round(randomDisk * 100.0) / 100.0;

        Metric metric = new Metric();
        metric.setDisk(roundedDisk);
        metric.setTimestamp(LocalDateTime.now());


        return metricRepository.save(metric);
    }

    @Scheduled(fixedRate = 5000)
    public void autoGenerateMetrics() {
        Metric metric = generateAndSaveSimulatedMetric();
        System.out.println("Automaticky uložená metrika: " + metric.getDisk() + "% o" + metric.getTimestamp());

    }

}