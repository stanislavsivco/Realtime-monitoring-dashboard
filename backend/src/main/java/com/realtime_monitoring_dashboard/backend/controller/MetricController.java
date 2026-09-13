package com.realtime_monitoring_dashboard.backend.controller;

import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.service.MetricService;
import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import lombok.RequiredArgsConstructor;

import org.apache.catalina.connector.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController 
@RequestMapping("/api/devices")
@RequiredArgsConstructor 
@CrossOrigin(origins = "*")

public class MetricController {

    private final MetricService metricService;

    @GetMapping("/{id}/metrics")
    public ResponseEntity<List<MetricDTO>> getMetricsByDeviceId (@PathVariable Long id) {
        return ResponseEntity.ok(metricService.getMetricsByDeviceId(id));
    }

    
    
}
