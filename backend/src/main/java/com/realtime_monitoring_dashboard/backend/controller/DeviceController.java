package com.realtime_monitoring_dashboard.backend.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtime_monitoring_dashboard.backend.service.DeviceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping ("/api/devices")
@RequiredArgsConstructor 
@CrossOrigin(origins = "*")

public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<List<Device>> getAllDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    @getMapping("/{id}")
    public ResponseEntity<Device> getDeviceByID(@PathVariable Long id) {
        return ResponseEntity.ok(deviceService.getDeviceByID(id));
    }

    
}
