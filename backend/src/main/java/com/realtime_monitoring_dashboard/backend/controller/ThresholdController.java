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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
@Tag(name = "Thresholds", description = "Per-device alert thresholds")
public class ThresholdController {

    private final ThresholdService thresholdService;

    @Operation(summary = "Get thresholds for a device",
            description = "Returns device thresholds, creating default ones if none exist yet")
    @ApiResponse(responseCode = "200", description = "Thresholds returned")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @GetMapping("/{id}/thresholds")
    public ResponseEntity<ThresholdDTO> getThresholds(
            @Parameter(description = "Device ID") @PathVariable @Positive Long id) {
        return ResponseEntity.ok(thresholdService.getThresholdByDeviceId(id));
    }

    @Operation(summary = "Update thresholds for a device")
    @ApiResponse(responseCode = "200", description = "Thresholds updated")
    @ApiResponse(responseCode = "400", description = "Invalid input data")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @PutMapping("/{id}/thresholds")
    public ResponseEntity<ThresholdDTO> updateThresholds(
            @Parameter(description = "Device ID") @PathVariable @Positive Long id,
            @RequestBody @Valid UpdateThresholdRequestDTO request) {
        return ResponseEntity.ok(thresholdService.updateThreshold(id, request));
    }
}