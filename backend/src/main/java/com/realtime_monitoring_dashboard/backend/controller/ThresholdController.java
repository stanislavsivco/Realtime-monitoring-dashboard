package com.realtime_monitoring_dashboard.backend.controller;

import com.realtime_monitoring_dashboard.backend.dto.ThresholdDTO;
import com.realtime_monitoring_dashboard.backend.dto.UpdateThresholdRequestDTO;
import com.realtime_monitoring_dashboard.backend.service.ThresholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Thresholds", description = "Per-device alert thresholds")
public class ThresholdController {

    private final ThresholdService thresholdService;

    @Operation(operationId = "getThresholds", summary = "Get thresholds for a device",
            description = "Returns the saved thresholds, or the default ones if an admin has not saved any yet")
    @ApiResponse(responseCode = "200", description = "Thresholds returned")
    @ApiResponse(responseCode = "400", description = "Invalid device ID")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @GetMapping("/{id}/thresholds")
    public ResponseEntity<ThresholdDTO> getThresholds(
            @Parameter(description = "Device ID") @PathVariable @Positive Long id) {
        return ResponseEntity.ok(thresholdService.getThresholdByDeviceId(id));
    }

    @Operation(operationId = "updateThresholds", summary = "Update thresholds for a device")
    @ApiResponse(responseCode = "200", description = "Thresholds updated")
    @ApiResponse(responseCode = "400", description = "Invalid device ID or input data, or a warning value is not lower than its critical value")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/thresholds")
    public ResponseEntity<ThresholdDTO> updateThresholds(
            @Parameter(description = "Device ID") @PathVariable @Positive Long id,
            @RequestBody @Valid UpdateThresholdRequestDTO request) {
        return ResponseEntity.ok(thresholdService.updateThreshold(id, request));
    }
}
