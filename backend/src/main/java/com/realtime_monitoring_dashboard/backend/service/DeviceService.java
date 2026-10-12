package com.realtime_monitoring_dashboard.backend.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<DeviceDTO> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .sorted((a, b) -> statusPriority(a.getStatus()) - statusPriority(b.getStatus()))
                .toList();
    }

    public DeviceDTO getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device with ID " + id + " does not exist"));
        return mapToDTO(device);
    }

    public DeviceDTO createDevice(CreateDeviceRequestDTO request) {
        Device device = Device.builder()
                .name(request.getName())
                .type(request.getType())
                .location(request.getLocation())
                .status(request.getStatus())
                .build();

        Device saved = deviceRepository.save(device);
        DeviceDTO dto = mapToDTO(saved);

        messagingTemplate.convertAndSend("/topic/devices", dto);

        return dto;
    }

    public DeviceDTO updateDevice(Long id, CreateDeviceRequestDTO request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device with ID " + id + " does not exist"));

        device.setName(request.getName());
        device.setType(request.getType());
        device.setLocation(request.getLocation());
        device.setStatus(request.getStatus());

        Device saved = deviceRepository.save(device);
        DeviceDTO dto = mapToDTO(saved);

        messagingTemplate.convertAndSend("/topic/devices", dto);

        return dto;
    }

    public void deleteDevice(Long id) {
        deviceRepository.deleteById(id);

        messagingTemplate.convertAndSend("/topic/devices/delete", id);
    }

    private DeviceDTO mapToDTO(Device device) {
        return DeviceDTO.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .location(device.getLocation())
                .status(device.getStatus())
                .simulated(device.isSimulated())
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

    public DeviceDTO registerAgent(RegisterAgentRequestDTO request) {
    return deviceRepository.findByName(request.getHostname())
            .map(this::mapToDTO)
            .orElseGet(() -> {
                Device device = Device.builder()
                        .name(request.getHostname())
                        .type(request.getType() != null ? request.getType() : "Agent")
                        .location(request.getLocation() != null ? request.getLocation() : "Unknown")
                        .status(DeviceStatus.ONLINE)
                        .simulated(false)
                        .build();

                Device saved = deviceRepository.save(device);
                DeviceDTO dto = mapToDTO(saved);
                messagingTemplate.convertAndSend("/topic/devices", dto);
                return dto;
            });
}
}