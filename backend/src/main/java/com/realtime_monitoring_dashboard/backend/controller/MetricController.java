package com.realtime_monitoring_dashboard.backend.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.realtime_monitoring_dashboard.backend.dto.IngestMetricRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO;
import com.realtime_monitoring_dashboard.backend.service.MetricService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Validated
public class MetricController {

    private final MetricService metricService;

    @GetMapping("/{id}/metrics")
    public ResponseEntity<Page<MetricDTO>> getMetricsByDeviceId(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(metricService.getMetricsByDeviceIdPaged(id, startDate, endDate, pageable));
    }

    @GetMapping("/{id}/metrics/latest")
    public ResponseEntity<MetricDTO> getLatestMetric(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(metricService.getLatestMetricByDeviceId(id));
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<MetricSummaryDTO> getDeviceAnalytics(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        return ResponseEntity.ok(metricService.getMetricSummary(id, startDate, endDate));
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingestMetric(@Valid @RequestBody IngestMetricRequestDTO request) {
        metricService.saveMetric(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
