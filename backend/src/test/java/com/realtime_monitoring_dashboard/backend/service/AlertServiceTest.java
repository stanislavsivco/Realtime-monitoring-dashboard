package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.realtime_monitoring_dashboard.backend.dto.AlertDTO;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private AlertService alertService;

    private static Device device() {
        return Device.builder().id(1L).name("Server-1").build();
    }

    private static Alert alert(Long id) {
        return Alert.builder().id(id).device(device()).severity(AlertSeverity.CRITICAL)
                .message("CPU too high").timestamp(LocalDateTime.now()).resolved(false).build();
    }

    @Test
    void createAlertPersistsUnresolvedAlertWithGivenData() {
        Device device = device();

        alertService.createAlert(device, AlertSeverity.WARNING, "Disk almost full");

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert saved = captor.getValue();
        assertThat(saved.getDevice()).isSameAs(device);
        assertThat(saved.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(saved.getMessage()).isEqualTo("Disk almost full");
        assertThat(saved.isResolved()).isFalse();
        assertThat(saved.isAcknowledged()).isFalse();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void getActiveAlertsMapsToDtos() {
        when(alertRepository.findByResolvedFalseOrderByTimestampDesc()).thenReturn(List.of(alert(7L)));

        List<AlertDTO> result = alertService.getActiveAlerts();

        assertThat(result).hasSize(1);
        AlertDTO dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getDeviceId()).isEqualTo(1L);
        assertThat(dto.getDeviceName()).isEqualTo("Server-1");
        assertThat(dto.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(dto.isResolved()).isFalse();
    }

    @Test
    void acknowledgeAlertMarksAcknowledgedAndBroadcasts() {
        Alert alert = alert(7L);
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertDTO dto = alertService.acknowledgeAlert(7L);

        assertThat(alert.isAcknowledged()).isTrue();
        assertThat(alert.isResolved()).isFalse();
        assertThat(dto.isAcknowledged()).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(AlertDTO.class));
    }

    @Test
    void acknowledgeAlertThrowsWhenMissing() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> alertService.acknowledgeAlert(99L));

        assertThat(ex.getMessage()).contains("99");
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void resolveAlertMarksResolvedAndBroadcasts() {
        Alert alert = alert(8L);
        when(alertRepository.findById(8L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertDTO dto = alertService.resolveAlert(8L);

        assertThat(alert.isResolved()).isTrue();
        assertThat(dto.isResolved()).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(AlertDTO.class));
    }

    @Test
    void resolveAlertThrowsWhenMissing() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> alertService.resolveAlert(99L));

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void resolveActiveAlertsForDeviceDoesNothingWhenNoActiveAlerts() {
        when(alertRepository.findByDeviceIdAndResolvedFalse(1L)).thenReturn(List.of());

        alertService.resolveActiveAlertsForDevice(device());

        verify(alertRepository, never()).save(any(Alert.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/alerts"), any(AlertDTO.class));
    }

    @Test
    void resolveActiveAlertsForDeviceResolvesAndBroadcastsEach() {
        Alert first = alert(1L);
        Alert second = alert(2L);
        when(alertRepository.findByDeviceIdAndResolvedFalse(1L)).thenReturn(List.of(first, second));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.resolveActiveAlertsForDevice(device());

        assertThat(first.isResolved()).isTrue();
        assertThat(second.isResolved()).isTrue();
        verify(alertRepository, times(2)).save(any(Alert.class));
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/alerts"), any(AlertDTO.class));
    }

    @Test
    void getAlertsPagedMapsPageContent() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Alert> page = new PageImpl<>(List.of(alert(3L)), pageable, 1);
        when(alertRepository.findAlertsFiltered(Boolean.FALSE, AlertSeverity.CRITICAL, pageable)).thenReturn(page);

        Page<AlertDTO> result = alertService.getAlertsPaged(Boolean.FALSE, AlertSeverity.CRITICAL, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(3L);
    }
}
