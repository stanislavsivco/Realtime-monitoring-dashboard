package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    
    public List<Device> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .sorted((a, b) -> statusPriority(a.getStatus()) - statusPriority(b.getStatus()))
                .toList();
    }

    
    public Device getDeviceById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));
    }

    
    private int statusPriority(DeviceStatus status) {
        return switch (status) {
            case CRITICAL -> 0;
            case OFFLINE -> 1;
            case WARNING -> 2;
            case ONLINE -> 3;
        };
    }
}