package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtime_monitoring_dashboard.backend.dto.BlockedDeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.DeviceBlockedException;
import com.realtime_monitoring_dashboard.backend.exception.DeviceNameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.BlockedDevice;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import com.realtime_monitoring_dashboard.backend.repository.BlockedDeviceRepository;
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
    private final BlockedDeviceRepository blockedDeviceRepository;
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
                .status(DeviceStatus.OFFLINE)
                .simulated(false)
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
        blockAgentMachine(device);

        messagingTemplate.convertAndSend("/topic/devices/delete", id);
    }

    public List<BlockedDeviceDTO> getBlockedDevices() {
        return blockedDeviceRepository.findAllByOrderByBlockedAtDesc()
                .stream()
                .map(this::mapToBlockedDTO)
                .toList();
    }

    public void unblockDevice(Long id) {
        BlockedDevice blockedDevice = blockedDeviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blocked device with ID " + id + " does not exist"));

        blockedDeviceRepository.delete(blockedDevice);
    }

    public DeviceDTO registerAgent(RegisterAgentRequestDTO request) {
        if (blockedDeviceRepository.existsByMachineIdHash(request.getMachineId())) {
            throw new DeviceBlockedException(
                    "This device was deleted by an administrator and is blocked from registering again");
        }

        return deviceRepository.findByMachineIdHash(request.getMachineId())
                .or(() -> adoptDeviceByName(request))
                .map(this::mapToDTO)
                .orElseGet(() -> createAgentDevice(request));
    }

    private Optional<Device> adoptDeviceByName(RegisterAgentRequestDTO request) {
        return deviceRepository.findByName(request.getHostname())
                .map(device -> {
                    if (device.getMachineIdHash() == null) {
                        device.setMachineIdHash(request.getMachineId());
                        return deviceRepository.saveAndFlush(device);
                    }
                    if (!device.getMachineIdHash().equals(request.getMachineId())) {
                        throw nameTaken(device.getName());
                    }
                    return device;
                });
    }

    private DeviceDTO createAgentDevice(RegisterAgentRequestDTO request) {
        Device device = Device.builder()
                .name(request.getHostname())
                .type(request.getType() != null ? request.getType() : "Agent")
                .location(request.getLocation() != null ? request.getLocation() : "Unknown")
                .status(DeviceStatus.ONLINE)
                .simulated(false)
                .machineIdHash(request.getMachineId())
                .build();

        Device saved;
        try {
            saved = deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException ex) {
            return deviceRepository.findByMachineIdHash(request.getMachineId())
                    .map(this::mapToDTO)
                    .orElseThrow(() -> deviceRepository.existsByName(request.getHostname())
                            ? nameTaken(request.getHostname())
                            : ex);
        }

        DeviceDTO dto = mapToDTO(saved);
        messagingTemplate.convertAndSend("/topic/devices", dto);
        return dto;
    }

    private void blockAgentMachine(Device device) {
        String machineIdHash = device.getMachineIdHash();
        if (machineIdHash == null || blockedDeviceRepository.existsByMachineIdHash(machineIdHash)) {
            return;
        }

        blockedDeviceRepository.save(BlockedDevice.builder()
                .machineIdHash(machineIdHash)
                .deviceName(device.getName())
                .blockedAt(LocalDateTime.now())
                .build());
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

    private BlockedDeviceDTO mapToBlockedDTO(BlockedDevice blockedDevice) {
        return BlockedDeviceDTO.builder()
                .id(blockedDevice.getId())
                .deviceName(blockedDevice.getDeviceName())
                .blockedAt(blockedDevice.getBlockedAt().atZone(ZoneId.systemDefault()).toInstant())
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
