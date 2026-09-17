package com.realtime_monitoring_dashboard.backend.controller;

import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.service.MetricService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<MetricDTO>> getMetricsByDeviceId(@PathVariable Long id) {
        return ResponseEntity.ok(metricService.getMetricsByDeviceId(id));
    }

    @GetMapping("/{deviceId}/metrics/latest")
    public ResponseEntity<MetricDTO> getLatestMetric(@PathVariable Long deviceId) {
        return ResponseEntity.ok(metricService.getLatestMetricByDeviceId(deviceId));
    }
}