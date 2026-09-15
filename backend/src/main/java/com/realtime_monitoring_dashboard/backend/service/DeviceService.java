package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
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

    public List<DeviceDTO> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .sorted((a, b) -> statusPriority(a.getStatus()) - statusPriority(b.getStatus()))
                .map(device -> DeviceDTO.builder()
                        .id(device.getId())
                        .name(device.getName())
                        .type(device.getType())
                        .location(device.getLocation())
                        .status(device.getStatus())
                        .build())
                .toList();
    }

    public DeviceDTO getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));
        return DeviceDTO.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .location(device.getLocation())
                .status(device.getStatus())
                .build();
    }

    private int statusPriority(DeviceStatus status) {
        return switch (status) {
            case CRITICAL -> 0;
            case OFFLINE -> 1;
            case WARNING -> 2;
            case ONLINE -> 3;
        };
    }

    public DeviceDTO createDevice(CreateDeviceRequestDTO request) {
        Device device = Device.builder()
            .name(request.getName())
            .type(request.getType())
            .location(request.getLocation())
            .status(request.getStatus())
            .build();

    Device saved = deviceRepository.save(device);

    return DeviceDTO.builder()
            .id(saved.getId())
            .name(saved.getName())
            .type(saved.getType())
            .location(saved.getLocation())
            .status(saved.getStatus())
            .build();


}

    public DeviceDTO updateDevice(Long id, CreateDeviceRequestDTO request) {
        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Device not found"));

        device.setName(request.getName());
        device.setType(request.getType());
        device.setLocation(request.getLocation());
        device.setStatus(request.getStatus());

        Device saved = deviceRepository.save(device);

        return DeviceDTO.builder()
                .id(saved.getId())
                .name(saved.getName())
                .type(saved.getType())
                .location(saved.getLocation())
                .status(saved.getStatus())
                .build();
}

    public void deleteDevice(Long id) {
        deviceRepository.deleteById(id);
    }
}