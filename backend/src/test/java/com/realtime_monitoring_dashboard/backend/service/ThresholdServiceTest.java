package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.realtime_monitoring_dashboard.backend.dto.ThresholdDTO;
import com.realtime_monitoring_dashboard.backend.dto.UpdateThresholdRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.InvalidThresholdException;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.Threshold;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.ThresholdRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThresholdServiceTest {

    @Mock
    private ThresholdRepository thresholdRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ThresholdService thresholdService;

    private static Device device(Long id) {
        return Device.builder().id(id).name("Server-" + id).type("Server").location("DC1").build();
    }

    private static UpdateThresholdRequestDTO validRequest() {
        return new UpdateThresholdRequestDTO(60.0, 80.0, 65.0, 85.0, 70.0, 95.0, 100, 300);
    }

    @Test
    void effectiveThresholdReturnsDefaultsWithoutSavingWhenNoneStored() {
        when(thresholdRepository.findByDeviceId(1L)).thenReturn(Optional.empty());

        Threshold threshold = thresholdService.getEffectiveThreshold(1L);

        assertThat(threshold.getId()).isNull();
        assertThat(threshold.getCpuWarning()).isEqualTo(75.0);
        assertThat(threshold.getCpuCritical()).isEqualTo(90.0);
        assertThat(threshold.getLatencyCriticalMs()).isEqualTo(500);
        verify(thresholdRepository, never()).save(any(Threshold.class));
    }

    @Test
    void effectiveThresholdReturnsStoredThreshold() {
        Threshold stored = Threshold.builder().id(7L).cpuWarning(50.0).cpuCritical(60.0).build();
        when(thresholdRepository.findByDeviceId(1L)).thenReturn(Optional.of(stored));

        assertThat(thresholdService.getEffectiveThreshold(1L)).isSameAs(stored);
    }

    @Test
    void getThresholdForDeviceWithoutStoredValuesReturnsDefaultsAndDoesNotWrite() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device(1L)));
        when(thresholdRepository.findByDeviceId(1L)).thenReturn(Optional.empty());

        ThresholdDTO dto = thresholdService.getThresholdByDeviceId(1L);

        assertThat(dto.getId()).isNull();
        assertThat(dto.getDeviceId()).isEqualTo(1L);
        assertThat(dto.getDiskWarning()).isEqualTo(75.0);
        verify(thresholdRepository, never()).save(any(Threshold.class));
    }

    @Test
    void getThresholdForMissingDeviceThrowsNotFound() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> thresholdService.getThresholdByDeviceId(99L));
    }

    @Test
    void updateCreatesThresholdLinkedToDeviceWhenNoneStored() {
        Device device = device(1L);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(thresholdRepository.findByDeviceId(1L)).thenReturn(Optional.empty());
        when(thresholdRepository.save(any(Threshold.class))).thenAnswer(inv -> inv.getArgument(0));

        ThresholdDTO dto = thresholdService.updateThreshold(1L, validRequest());

        ArgumentCaptor<Threshold> captor = ArgumentCaptor.forClass(Threshold.class);
        verify(thresholdRepository).save(captor.capture());
        assertThat(captor.getValue().getDevice()).isSameAs(device);
        assertThat(captor.getValue().getCpuWarning()).isEqualTo(60.0);
        assertThat(captor.getValue().getLatencyCriticalMs()).isEqualTo(300);
        assertThat(dto.getDeviceId()).isEqualTo(1L);
    }

    @Test
    void updatePublishesThresholdUpdatedEventSoStatusIsRecalculated() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device(1L)));
        when(thresholdRepository.findByDeviceId(1L)).thenReturn(Optional.empty());
        when(thresholdRepository.save(any(Threshold.class))).thenAnswer(inv -> inv.getArgument(0));

        thresholdService.updateThreshold(1L, validRequest());

        verify(eventPublisher).publishEvent(new ThresholdUpdatedEvent(1L));
    }

    @Test
    void updateChangesExistingThreshold() {
        Threshold stored = Threshold.builder().id(7L).device(device(1L)).cpuWarning(75.0).cpuCritical(90.0).build();
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device(1L)));
        when(thresholdRepository.findByDeviceId(1L)).thenReturn(Optional.of(stored));
        when(thresholdRepository.save(any(Threshold.class))).thenAnswer(inv -> inv.getArgument(0));

        ThresholdDTO dto = thresholdService.updateThreshold(1L, validRequest());

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(stored.getCpuWarning()).isEqualTo(60.0);
    }

    @Test
    void updateForMissingDeviceThrowsNotFoundWithoutSaving() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> thresholdService.updateThreshold(99L, validRequest()));

        verify(thresholdRepository, never()).save(any(Threshold.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateRejectsWarningNotBelowCriticalBeforeTouchingDatabase() {
        UpdateThresholdRequestDTO request = new UpdateThresholdRequestDTO(90.0, 90.0, 65.0, 85.0, 70.0, 95.0, 100, 300);

        InvalidThresholdException ex = assertThrows(InvalidThresholdException.class,
                () -> thresholdService.updateThreshold(1L, request));

        assertThat(ex.getMessage()).contains("cpuWarning");
        verifyNoInteractions(thresholdRepository, deviceRepository, eventPublisher);
    }
}
