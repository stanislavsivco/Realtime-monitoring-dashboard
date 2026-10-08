package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private DeviceService deviceService;

    private static Device device(Long id, String name, DeviceStatus status) {
        return Device.builder().id(id).name(name).type("Server").location("DC1").status(status).build();
    }

    @Test
    void getAllDevicesSortsByStatusPriority() {
        when(deviceRepository.findAll()).thenReturn(List.of(
                device(1L, "online-dev", DeviceStatus.ONLINE),
                device(2L, "warning-dev", DeviceStatus.WARNING),
                device(3L, "critical-dev", DeviceStatus.CRITICAL),
                device(4L, "offline-dev", DeviceStatus.OFFLINE)));

        List<DeviceDTO> result = deviceService.getAllDevices();

        assertThat(result).extracting(DeviceDTO::getName)
                .containsExactly("critical-dev", "offline-dev", "warning-dev", "online-dev");
    }

    @Test
    void getAllDevicesReturnsEmptyListWhenNoDevices() {
        when(deviceRepository.findAll()).thenReturn(List.of());

        assertThat(deviceService.getAllDevices()).isEmpty();
    }

    @Test
    void getAllDevicesReflectsPersistedStatusDirectly() {
        when(deviceRepository.findAll()).thenReturn(List.of(device(1L, "d", DeviceStatus.WARNING)));

        assertThat(deviceService.getAllDevices().get(0).getStatus()).isEqualTo(DeviceStatus.WARNING);
    }

    @Test
    void getDeviceByIdReturnsMappedDto() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device(1L, "Server-1", DeviceStatus.ONLINE)));

        DeviceDTO dto = deviceService.getDeviceById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Server-1");
        assertThat(dto.getType()).isEqualTo("Server");
        assertThat(dto.getLocation()).isEqualTo("DC1");
        assertThat(dto.getStatus()).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void getDeviceByIdThrowsWhenMissing() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> deviceService.getDeviceById(99L));

        assertThat(ex.getMessage()).contains("99");
    }

    @Test
    void createDeviceSavesAndBroadcasts() {
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.setId(10L);
            return d;
        });

        DeviceDTO result = deviceService.createDevice(
                new CreateDeviceRequestDTO("Router-1", "Router", "Kosice", DeviceStatus.WARNING));

        ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Router-1");
        assertThat(saved.getValue().getType()).isEqualTo("Router");
        assertThat(saved.getValue().getLocation()).isEqualTo("Kosice");
        assertThat(saved.getValue().getStatus()).isEqualTo(DeviceStatus.WARNING);

        assertThat(saved.getValue().isSimulated()).isTrue();

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.isSimulated()).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void updateDeviceChangesFieldsAndBroadcasts() {
        Device existing = device(5L, "old-name", DeviceStatus.ONLINE);
        when(deviceRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceDTO result = deviceService.updateDevice(5L,
                new CreateDeviceRequestDTO("new-name", "Switch", "Bratislava", DeviceStatus.WARNING));

        assertThat(existing.getName()).isEqualTo("new-name");
        assertThat(existing.getType()).isEqualTo("Switch");
        assertThat(existing.getLocation()).isEqualTo("Bratislava");
        assertThat(existing.getStatus()).isEqualTo(DeviceStatus.WARNING);
        assertThat(result.getName()).isEqualTo("new-name");
        verify(messagingTemplate).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void updateDeviceThrowsWhenMissing() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> deviceService.updateDevice(99L,
                new CreateDeviceRequestDTO("n", "t", "l", DeviceStatus.ONLINE)));

        verify(deviceRepository, never()).save(any(Device.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void deleteDeviceCallsRepositoryAndBroadcasts() {
        deviceService.deleteDevice(3L);

        verify(deviceRepository).deleteById(3L);
        verify(messagingTemplate).convertAndSend("/topic/devices/delete", 3L);
    }

    @Test
    void registerAgentReturnsExistingDeviceWithoutCreatingNew() {
        Device existing = device(7L, "web-01.local", DeviceStatus.ONLINE);
        when(deviceRepository.findByName("web-01.local")).thenReturn(Optional.of(existing));

        DeviceDTO result = deviceService.registerAgent(
                new RegisterAgentRequestDTO("web-01.local", "Server", "DC1"));

        assertThat(result.getId()).isEqualTo(7L);
        verify(deviceRepository, never()).save(any(Device.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void registerAgentCreatesNewDeviceWhenHostnameUnknown() {
        when(deviceRepository.findByName("new-agent")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.setId(42L);
            return d;
        });

        DeviceDTO result = deviceService.registerAgent(
                new RegisterAgentRequestDTO("new-agent", "Server", "DC2"));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("new-agent");
        assertThat(captor.getValue().getType()).isEqualTo("Server");
        assertThat(captor.getValue().getLocation()).isEqualTo("DC2");
        assertThat(captor.getValue().getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(captor.getValue().isSimulated()).isFalse();
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.isSimulated()).isFalse();
        verify(messagingTemplate).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void registerAgentFallsBackToDefaultsWhenTypeAndLocationMissing() {
        when(deviceRepository.findByName("bare-agent")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        deviceService.registerAgent(new RegisterAgentRequestDTO("bare-agent", null, null));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("Agent");
        assertThat(captor.getValue().getLocation()).isEqualTo("Unknown");
    }
}
