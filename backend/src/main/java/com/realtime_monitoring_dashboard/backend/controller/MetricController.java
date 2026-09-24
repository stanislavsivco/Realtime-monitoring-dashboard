package com.realtime_monitoring_dashboard.backend.controller;

import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.service.MetricService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController 
@RequestMapping("/api/devices")
@RequiredArgsConstructor 
@CrossOrigin(origins = "*")
@Validated
@Tag(name = "Metrics", description = "History and present device metrics")
public class MetricController {

    private final MetricService metricService;

    @GetMapping("/{id}/metrics")
    public ResponseEntity<Page<MetricDTO>> getMetricsByDeviceId(
            @Parameter(description = "Device ID") @PathVariable @Positive Long id,
            @Parameter(description = "Start of time interval")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End of time interval")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(metricService.getMetricsByDeviceIdPaged(id, startDate, endDate, pageable));
    }

    @Operation(summary = "Newest device metric")
    @ApiResponse(responseCode = "200", description = "Metric found")
    @ApiResponse(responseCode = "404", description = "Device does not have any metrics or does not exist")
    @GetMapping("/{deviceId}/metrics/latest")
    public ResponseEntity<MetricDTO> getLatestMetric(@Parameter(description = "Device ID")@PathVariable @Positive Long deviceId) {
        return ResponseEntity.ok(metricService.getLatestMetricByDeviceId(deviceId));
    }
}