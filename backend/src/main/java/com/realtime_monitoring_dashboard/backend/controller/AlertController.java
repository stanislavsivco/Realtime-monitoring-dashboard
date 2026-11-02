package com.realtime_monitoring_dashboard.backend.controller;

import com.realtime_monitoring_dashboard.backend.dto.AlertDTO;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.service.AlertService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Validated
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<Page<AlertDTO>> getAlerts(
            @RequestParam(required = false) Boolean resolved,

            @RequestParam(required = false) AlertSeverity severity,

            @PageableDefault(size = 15, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(alertService.getAlertsPaged(resolved, severity, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<AlertDTO> acknowledgeAlert(
            @PathVariable @Positive Long id) {
        return ResponseEntity.ok(alertService.acknowledgeAlert(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<AlertDTO> resolveAlert(
            @PathVariable @Positive Long id) {
        return ResponseEntity.ok(alertService.resolveAlert(id));
    }
}
