package com.realtime_monitoring_dashboard.backend.controller;

import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.service.MetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController 
@RequestMapping("/api/devices")
@RequiredArgsConstructor 
@CrossOrigin(origins = "*")
public class MetricController {

    private final MetricService metricService;

    @GetMapping("/{id}/metrics")
    public ResponseEntity<Page<MetricDTO>> getMetricsByDeviceId(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(metricService.getMetricsByDeviceIdPaged(id, startDate, endDate, pageable));
    }

    @GetMapping("/{deviceId}/metrics/latest")
    public ResponseEntity<MetricDTO> getLatestMetric(@PathVariable Long deviceId) {
        return ResponseEntity.ok(metricService.getLatestMetricByDeviceId(deviceId));
    }
}