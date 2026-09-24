package com.realtime_monitoring_dashboard.backend.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import java.util.List;

import com.realtime_monitoring_dashboard.backend.service.DeviceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping ("/api/devices")
@RequiredArgsConstructor 
@CrossOrigin(origins = "*")
@Validated
@Tag(name = "Devices", description = "Monitored devices")

public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "List of all devices", description = "Returns all devices by status (CRITICAL → OFFLINE → WARNING → ONLINE)")
    @ApiResponse(responseCode = "200", description = "List send successfully")
    @GetMapping
    public ResponseEntity<List<DeviceDTO>> getAllDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    @Operation(summary = "Detail of devices by ID")
    @ApiResponse(responseCode = "200", description = "Device found")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @GetMapping("/{id}")
    public ResponseEntity<DeviceDTO> getDeviceByID(@Parameter(description = "Device ID")@PathVariable @Positive Long id) {
        return ResponseEntity.ok(deviceService.getDeviceById(id));
    }

    @Operation(summary = "Create new device")
    @ApiResponse(responseCode = "200", description = "Device created")
    @ApiResponse(responseCode = "400", description = "Invalid input data")
    @PostMapping 
    public ResponseEntity<DeviceDTO> createDevice (@RequestBody @Valid CreateDeviceRequestDTO request) {
        return ResponseEntity.ok(deviceService.createDevice(request));
    }

    @Operation(summary = "Edit existing device")
    @ApiResponse(responseCode = "200", description = "Device edited")
    @ApiResponse(responseCode = "400", description = "Invalid input data")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @PutMapping("/{id}")
    public ResponseEntity<DeviceDTO> updateDevice(@Parameter(description = "Device ID")@PathVariable @Positive Long id, @RequestBody @Valid CreateDeviceRequestDTO request) {
        return ResponseEntity.ok(deviceService.updateDevice(id, request));
    }

    @Operation(summary = "Delete device")
    @ApiResponse(responseCode = "204", description = "Device deleted")
    @ApiResponse(responseCode = "404", description = "Device with this ID does not exist")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@Parameter(description = "Device ID")@PathVariable @Positive Long id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

}