package com.realtime_monitoring_dashboard.backend.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.DeviceNameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import com.realtime_monitoring_dashboard.backend.repository.ThresholdRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final MetricRepository metricRepository;
    private final AlertRepository alertRepository;
    private final ThresholdRepository thresholdRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<DeviceDTO> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .sorted((a, b) -> statusPriority(a.getStatus()) - statusPriority(b.getStatus()))
                .toList();
    }

    public DeviceDTO getDeviceById(Long id) {
        return mapToDTO(findDevice(id));
    }

    public DeviceDTO createDevice(CreateDeviceRequestDTO request) {
        if (deviceRepository.existsByName(request.getName())) {
            throw nameTaken(request.getName());
        }

        Device device = Device.builder()
                .name(request.getName())
                .type(request.getType())
                .location(request.getLocation())
                .status(request.getStatus())
                .build();

        DeviceDTO dto = mapToDTO(saveWithUniqueName(device));
        messagingTemplate.convertAndSend("/topic/devices", dto);
        return dto;
    }

    public DeviceDTO updateDevice(Long id, CreateDeviceRequestDTO request) {
        Device device = findDevice(id);

        if (deviceRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw nameTaken(request.getName());
        }

        device.setName(request.getName());
        device.setType(request.getType());
        device.setLocation(request.getLocation());
        device.setStatus(request.getStatus());

        DeviceDTO dto = mapToDTO(saveWithUniqueName(device));
        messagingTemplate.convertAndSend("/topic/devices", dto);
        return dto;
    }

    @Transactional
    public void deleteDevice(Long id) {
        Device device = findDevice(id);

        metricRepository.deleteByDeviceId(id);
        alertRepository.deleteByDeviceId(id);
        thresholdRepository.deleteByDeviceId(id);
        deviceRepository.delete(device);
        deviceRepository.flush();

        messagingTemplate.convertAndSend("/topic/devices/delete", id);
    }

    public DeviceDTO registerAgent(RegisterAgentRequestDTO request) {
        return deviceRepository.findByName(request.getHostname())
                .map(this::mapToDTO)
                .orElseGet(() -> createAgentDevice(request));
    }

    private DeviceDTO createAgentDevice(RegisterAgentRequestDTO request) {
        Device device = Device.builder()
                .name(request.getHostname())
                .type(request.getType() != null ? request.getType() : "Agent")
                .location(request.getLocation() != null ? request.getLocation() : "Unknown")
                .status(DeviceStatus.ONLINE)
                .simulated(false)
                .build();

        Device saved;
        try {
            saved = deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException ex) {
            return deviceRepository.findByName(request.getHostname())
                    .map(this::mapToDTO)
                    .orElseThrow(() -> ex);
        }

        DeviceDTO dto = mapToDTO(saved);
        messagingTemplate.convertAndSend("/topic/devices", dto);
        return dto;
    }

    private Device findDevice(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device with ID " + id + " does not exist"));
    }

    private Device saveWithUniqueName(Device device) {
        try {
            return deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException ex) {
            throw nameTaken(device.getName());
        }
    }

    private DeviceNameAlreadyExistsException nameTaken(String name) {
        return new DeviceNameAlreadyExistsException("Device with name '" + name + "' already exists");
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
}
