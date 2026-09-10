package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Random;

@Service 
@RequiredArgsConstructor 

public class MetricService {

    private final MetricRepository metricRepository;
    private final Random random = new Random();
    
    
}
