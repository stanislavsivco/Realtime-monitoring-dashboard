package com.realtime_monitoring_dashboard.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.service.DeviceService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Validated
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<List<DeviceDTO>> getAllDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceDTO> getDeviceByID(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(deviceService.getDeviceById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<DeviceDTO> createDevice(@RequestBody @Valid CreateDeviceRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.createDevice(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<DeviceDTO> updateDevice(@PathVariable @Positive Long id,
            @RequestBody @Valid CreateDeviceRequestDTO request) {
        return ResponseEntity.ok(deviceService.updateDevice(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable @Positive Long id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<DeviceDTO> registerAgent(@RequestBody @Valid RegisterAgentRequestDTO request) {
        return ResponseEntity.ok(deviceService.registerAgent(request));
    }
}
