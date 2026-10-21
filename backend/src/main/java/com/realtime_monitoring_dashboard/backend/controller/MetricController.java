package com.realtime_monitoring_dashboard.backend.controller;

import java.time.LocalDateTime;

import org.springdoc.core.annotations.ParameterObject;
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

import com.realtime_monitoring_dashboard.backend.config.OpenApiConfig;
import com.realtime_monitoring_dashboard.backend.dto.IngestMetricRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO;
import com.realtime_monitoring_dashboard.backend.service.MetricService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Validated
@Tag(name = "Metrics", description = "History and present device metrics")
public class MetricController {

    private final MetricService metricService;

    @Operation(operationId = "getMetricHistory", summary = "Metric history of a device",
            description = "Paged, newest first. Optional startDate and endDate filter the time interval")
    @ApiResponse(responseCode = "200", description = "Page of metrics returned")
    @ApiResponse(responseCode = "400", description = "Invalid device ID, date or paging value, or startDate is after endDate")
    @GetMapping("/{id}/metrics")
    public ResponseEntity<Page<MetricDTO>> getMetricsByDeviceId(
            @Parameter(description = "Device ID") @PathVariable @Positive Long id,
            @Parameter(description = "Start of time interval")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End of time interval")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @ParameterObject @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(metricService.getMetricsByDeviceIdPaged(id, startDate, endDate, pageable));
    }

    @Operation(operationId = "getLatestMetric", summary = "Newest device metric")
    @ApiResponse(responseCode = "200", description = "Metric found")
    @ApiResponse(responseCode = "400", description = "Invalid device ID")
    @ApiResponse(responseCode = "404", description = "Device does not have any metrics or does not exist")
    @GetMapping("/{id}/metrics/latest")
    public ResponseEntity<MetricDTO> getLatestMetric(@Parameter(description = "Device ID") @PathVariable @Positive Long id) {
        return ResponseEntity.ok(metricService.getLatestMetricByDeviceId(id));
    }

    @Operation(operationId = "getMetricSummary", summary = "Metric summary of a device",
            description = "Average, maximum and minimum values. Defaults to the last 7 days")
    @ApiResponse(responseCode = "200", description = "Summary returned")
    @ApiResponse(responseCode = "400", description = "Invalid device ID or date, or startDate is after endDate")
    @GetMapping("/{id}/analytics")
    public ResponseEntity<MetricSummaryDTO> getDeviceAnalytics(
            @Parameter(description = "Device ID") @PathVariable @Positive Long id,
            @Parameter(description = "Start of time interval")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End of time interval")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        return ResponseEntity.ok(metricService.getMetricSummary(id, startDate, endDate));
    }

    @Operation(operationId = "ingestMetric", summary = "Ingest a metric from an agent",
            description = "The server sets the timestamp. cpu, ram and disk are percentages between 0 and 100")
    @ApiResponse(responseCode = "201", description = "Metric stored")
    @ApiResponse(responseCode = "400", description = "Invalid metric values")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @SecurityRequirement(name = OpenApiConfig.AGENT_KEY)
    @PostMapping("/ingest")
    public ResponseEntity<Void> ingestMetric(@Valid @RequestBody IngestMetricRequestDTO request) {
        metricService.saveMetric(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
