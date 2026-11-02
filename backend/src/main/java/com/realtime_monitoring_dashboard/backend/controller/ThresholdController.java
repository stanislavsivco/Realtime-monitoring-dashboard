package com.realtime_monitoring_dashboard.backend.controller;

import com.realtime_monitoring_dashboard.backend.dto.ThresholdDTO;
import com.realtime_monitoring_dashboard.backend.dto.UpdateThresholdRequestDTO;
import com.realtime_monitoring_dashboard.backend.service.ThresholdService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Validated
public class ThresholdController {

    private final ThresholdService thresholdService;

    @GetMapping("/{id}/thresholds")
    public ResponseEntity<ThresholdDTO> getThresholds(
            @PathVariable @Positive Long id) {
        return ResponseEntity.ok(thresholdService.getThresholdByDeviceId(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/thresholds")
    public ResponseEntity<ThresholdDTO> updateThresholds(
            @PathVariable @Positive Long id,
            @RequestBody @Valid UpdateThresholdRequestDTO request) {
        return ResponseEntity.ok(thresholdService.updateThreshold(id, request));
    }
}
