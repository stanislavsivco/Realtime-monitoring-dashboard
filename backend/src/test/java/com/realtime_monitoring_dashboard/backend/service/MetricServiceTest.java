package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricServiceTest {

    @Mock
    private MetricRepository metricRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private AlertService alertService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private DeviceStatusCalculator statusCalculator;

    @InjectMocks
    private MetricService metricService;

    private static Device device(Long id, String name, DeviceStatus status) {
        return Device.builder().id(id).name(name).type("Server").location("DC1").status(status).build();
    }

    private static Metric metric(Long id, Device device) {
        return Metric.builder().id(id).device(device).timestamp(LocalDateTime.now())
                .cpu(45.5).ram(60.0).disk(30.0).latencyMs(20)
                .networkInMbps(100.0).networkOutMbps(50.0).build();
    }

    private void stubSavesToEchoArguments() {
        when(metricRepository.save(any(Metric.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void saveMetricStoresAllFieldsIncludingNetworkThroughput() {
        Device device = device(1L, "Server-1", DeviceStatus.ONLINE);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.ONLINE);
        stubSavesToEchoArguments();
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 1, 12, 0);

        MetricDTO input = MetricDTO.builder().deviceId(1L).timestamp(timestamp)
                .cpu(45.5).ram(60.0).disk(30.0).latencyMs(20)
                .networkInMbps(123.4).networkOutMbps(56.7).build();

        MetricDTO result = metricService.saveMetric(input);

        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
        verify(metricRepository).save(captor.capture());
        Metric saved = captor.getValue();
        assertThat(saved.getDevice()).isSameAs(device);
        assertThat(saved.getCpu()).isEqualTo(45.5);
        assertThat(saved.getNetworkInMbps()).isEqualTo(123.4);
        assertThat(saved.getNetworkOutMbps()).isEqualTo(56.7);
        assertThat(saved.getTimestamp()).isEqualTo(timestamp);

        assertThat(result.getDeviceId()).isEqualTo(1L);
        assertThat(result.getNetworkInMbps()).isEqualTo(123.4);
        verify(messagingTemplate).convertAndSend(eq("/topic/metrics"), any(MetricDTO.class));
    }

    @Test
    void saveMetricUsesCurrentTimeWhenTimestampMissing() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device(1L, "Server-1", DeviceStatus.ONLINE)));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.ONLINE);
        stubSavesToEchoArguments();
        LocalDateTime before = LocalDateTime.now();

        metricService.saveMetric(MetricDTO.builder().deviceId(1L).cpu(10.0).ram(10.0).disk(10.0).build());

        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
        verify(metricRepository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void saveMetricThrowsNotFoundWhenDeviceMissing() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        MetricDTO input = MetricDTO.builder().deviceId(99L).cpu(10.0).ram(10.0).disk(10.0).build();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> metricService.saveMetric(input));

        assertThat(ex.getMessage()).contains("99");
        verify(metricRepository, never()).save(any(Metric.class));
    }

    @Test
    void transitionToCriticalResolvesOldAlertsAndCreatesCriticalAlert() {
        Device device = device(1L, "Server-1", DeviceStatus.ONLINE);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.CRITICAL);
        stubSavesToEchoArguments();

        metricService.saveMetric(MetricDTO.builder().deviceId(1L).cpu(95.0).ram(50.0).disk(50.0).build());

        verify(alertService).resolveActiveAlertsForDevice(same(device));
        verify(alertService).createAlert(same(device), eq(AlertSeverity.CRITICAL), contains("Server-1"));
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.CRITICAL);
    }

    @Test
    void transitionToWarningResolvesOldAlertsAndCreatesWarningAlert() {
        Device device = device(1L, "Server-1", DeviceStatus.ONLINE);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.WARNING);
        stubSavesToEchoArguments();

        metricService.saveMetric(MetricDTO.builder().deviceId(1L).cpu(80.0).ram(50.0).disk(50.0).build());

        verify(alertService).resolveActiveAlertsForDevice(same(device));
        verify(alertService).createAlert(same(device), eq(AlertSeverity.WARNING), anyString());
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.WARNING);
    }

    @Test
    void transitionToOnlineOnlyResolvesWithoutCreatingAlert() {
        Device device = device(1L, "Server-1", DeviceStatus.CRITICAL);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.ONLINE);
        stubSavesToEchoArguments();

        metricService.saveMetric(MetricDTO.builder().deviceId(1L).cpu(10.0).ram(10.0).disk(10.0).build());

        verify(alertService).resolveActiveAlertsForDevice(same(device));
        verify(alertService, never()).createAlert(any(Device.class), any(AlertSeverity.class), anyString());
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void unchangedStatusDoesNotTriggerResolve() {
        Device device = device(1L, "Server-1", DeviceStatus.ONLINE);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.ONLINE);
        stubSavesToEchoArguments();

        metricService.saveMetric(MetricDTO.builder().deviceId(1L).cpu(10.0).ram(10.0).disk(10.0).build());

        verify(alertService, never()).resolveActiveAlertsForDevice(any(Device.class));
        verify(alertService, never()).createAlert(any(Device.class), any(AlertSeverity.class), anyString());
    }

    @Test
    void repeatedCriticalReadingsKeepCreatingAlertsWithoutResolving() {
        Device device = device(1L, "Server-1", DeviceStatus.CRITICAL);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.CRITICAL);
        stubSavesToEchoArguments();

        metricService.saveMetric(MetricDTO.builder().deviceId(1L).cpu(95.0).ram(50.0).disk(50.0).build());

        verify(alertService, never()).resolveActiveAlertsForDevice(any(Device.class));
        verify(alertService).createAlert(same(device), eq(AlertSeverity.CRITICAL), anyString());
    }

    @Test
    void checkOfflineDevicesMarksDeviceOfflineWhenNoMetricsExist() {
        Device device = device(1L, "Server-1", DeviceStatus.ONLINE);
        when(deviceRepository.findAll()).thenReturn(List.of(device));
        when(metricRepository.findTopByDeviceIdOrderByTimestampDesc(1L)).thenReturn(Optional.empty());
        stubSavesToEchoArguments();

        metricService.checkOfflineDevices();

        assertThat(device.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
        verify(deviceRepository).save(device);
        verify(messagingTemplate).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void checkOfflineDevicesMarksDeviceOfflineWhenLatestMetricIsStale() {
        Device device = device(1L, "Server-1", DeviceStatus.WARNING);
        when(deviceRepository.findAll()).thenReturn(List.of(device));
        when(metricRepository.findTopByDeviceIdOrderByTimestampDesc(1L)).thenReturn(
                Optional.of(Metric.builder().timestamp(LocalDateTime.now().minusSeconds(120)).build()));
        stubSavesToEchoArguments();

        metricService.checkOfflineDevices();

        assertThat(device.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
        verify(deviceRepository).save(device);
    }

    @Test
    void checkOfflineDevicesLeavesFreshDeviceUntouched() {
        Device device = device(1L, "Server-1", DeviceStatus.ONLINE);
        when(deviceRepository.findAll()).thenReturn(List.of(device));
        when(metricRepository.findTopByDeviceIdOrderByTimestampDesc(1L)).thenReturn(
                Optional.of(Metric.builder().timestamp(LocalDateTime.now()).build()));

        metricService.checkOfflineDevices();

        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        verify(deviceRepository, never()).save(any(Device.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/devices"), any(DeviceDTO.class));
    }

    @Test
    void checkOfflineDevicesSkipsDevicesAlreadyOffline() {
        Device device = device(1L, "Server-1", DeviceStatus.OFFLINE);
        when(deviceRepository.findAll()).thenReturn(List.of(device));

        metricService.checkOfflineDevices();

        verify(metricRepository, never()).findTopByDeviceIdOrderByTimestampDesc(any(Long.class));
        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void getLatestMetricReturnsMappedDto() {
        when(metricRepository.findTopByDeviceIdOrderByTimestampDesc(1L))
                .thenReturn(Optional.of(metric(5L, device(1L, "Server-1", DeviceStatus.ONLINE))));

        MetricDTO dto = metricService.getLatestMetricByDeviceId(1L);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getCpu()).isEqualTo(45.5);
        assertThat(dto.getNetworkOutMbps()).isEqualTo(50.0);
    }

    @Test
    void getLatestMetricThrowsWhenNoneExist() {
        when(metricRepository.findTopByDeviceIdOrderByTimestampDesc(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> metricService.getLatestMetricByDeviceId(99L));
    }

    @Test
    void getMetricsByDeviceIdKeepsRepositoryOrder() {
        Device device = device(1L, "Server-1", DeviceStatus.ONLINE);
        when(metricRepository.findTop120ByDeviceIdOrderByTimestampAsc(1L))
                .thenReturn(List.of(metric(1L, device), metric(2L, device), metric(3L, device)));

        assertThat(metricService.getMetricsByDeviceId(1L)).extracting(MetricDTO::getId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void pagedMetricsUseDateFilterWhenBothDatesGiven() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 30, 23, 59);
        Page<Metric> page = new PageImpl<>(List.of(metric(1L, device(1L, "Server-1", DeviceStatus.ONLINE))), pageable, 1);
        when(metricRepository.findByDeviceIdAndTimestampBetween(1L, start, end, pageable)).thenReturn(page);

        Page<MetricDTO> result = metricService.getMetricsByDeviceIdPaged(1L, start, end, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(metricRepository, never()).findByDeviceId(any(Long.class), any(Pageable.class));
    }

    @Test
    void pagedMetricsIgnoreFilterWhenDatesMissing() {
        Pageable pageable = PageRequest.of(0, 20);
        when(metricRepository.findByDeviceId(1L, pageable)).thenReturn(new PageImpl<>(List.of()));

        metricService.getMetricsByDeviceIdPaged(1L, null, null, pageable);

        verify(metricRepository, never()).findByDeviceIdAndTimestampBetween(
                any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    void pagedMetricsIgnoreFilterWhenOnlyOneDateGiven() {
        Pageable pageable = PageRequest.of(0, 20);
        when(metricRepository.findByDeviceId(1L, pageable)).thenReturn(new PageImpl<>(List.of()));

        metricService.getMetricsByDeviceIdPaged(1L, LocalDateTime.now(), null, pageable);

        verify(metricRepository).findByDeviceId(1L, pageable);
    }

    @Test
    void summaryDefaultsToLastSevenDays() {
        MetricSummaryDTO summary = MetricSummaryDTO.builder().avgCpu(42.0).build();
        when(metricRepository.getMetricSummary(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(summary);
        LocalDateTime before = LocalDateTime.now();

        MetricSummaryDTO result = metricService.getMetricSummary(1L, null, null);

        LocalDateTime after = LocalDateTime.now();
        assertThat(result).isSameAs(summary);
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(metricRepository).getMetricSummary(eq(1L), start.capture(), end.capture());
        assertThat(start.getValue()).isBetween(before.minusDays(7), after.minusDays(7));
        assertThat(end.getValue()).isBetween(before, after);
    }

    @Test
    void summaryPassesExplicitDatesThrough() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 2, 0, 0);

        metricService.getMetricSummary(1L, start, end);

        verify(metricRepository).getMetricSummary(1L, start, end);
    }

    @Test
    void autoGenerateDoesNothingWhenThereAreNoDevices() {
        when(deviceRepository.findAll()).thenReturn(List.of());

        metricService.autoGenerateMetrics();

        verify(metricRepository, never()).save(any(Metric.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/metrics"), any(MetricDTO.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Server", "Database", "Router", "Storage", "Computer", "Laptop"})
    void autoGenerateProcessesSimulatedDevicesOfEveryType(String simulatedType) {
        Device device = device(1L, "Simulated-1", DeviceStatus.ONLINE);
        device.setType(simulatedType);
        device.setSimulated(true);
        when(deviceRepository.findAll()).thenReturn(List.of(device));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.ONLINE);
        stubSavesToEchoArguments();

        metricService.autoGenerateMetrics();

        verify(metricRepository).save(any(Metric.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/metrics"), any(MetricDTO.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Windows", "Linux", "Darwin", "Agent", "Server"})
    void autoGenerateSkipsAgentDevicesOfEveryType(String agentType) {
        Device device = device(1L, "agent-host", DeviceStatus.ONLINE);
        device.setType(agentType);
        device.setSimulated(false);
        when(deviceRepository.findAll()).thenReturn(List.of(device));

        metricService.autoGenerateMetrics();

        verify(deviceRepository, never()).findById(any(Long.class));
        verify(metricRepository, never()).save(any(Metric.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/metrics"), any(MetricDTO.class));
    }

    @Test
    void autoGenerateOnlyTouchesSimulatedDevicesInAMixedFleet() {
        Device seeded = device(1L, "Web Server 01", DeviceStatus.ONLINE);
        seeded.setType("Server");
        seeded.setSimulated(true);
        Device agent = device(2L, "johns-laptop", DeviceStatus.ONLINE);
        agent.setType("Windows");
        agent.setSimulated(false);
        when(deviceRepository.findAll()).thenReturn(List.of(seeded, agent));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(seeded));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.ONLINE);
        stubSavesToEchoArguments();

        metricService.autoGenerateMetrics();

        verify(deviceRepository, never()).findById(2L);
        verify(metricRepository, times(1)).save(any(Metric.class));
    }

    @Test
    void autoGenerateCreatesMetricsInExpectedRangesForEveryDevice() {
        List<Device> devices = List.of(
                device(1L, "Web Server 01", DeviceStatus.ONLINE),
                device(2L, "DB Server", DeviceStatus.ONLINE));
        devices.get(0).setType("Server");
        devices.get(1).setType("Database");
        when(deviceRepository.findAll()).thenReturn(devices);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(devices.get(0)));
        when(deviceRepository.findById(2L)).thenReturn(Optional.of(devices.get(1)));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.ONLINE);
        stubSavesToEchoArguments();

        metricService.autoGenerateMetrics();

        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
        verify(metricRepository, times(2)).save(captor.capture());
        for (Metric m : captor.getAllValues()) {
            assertThat(m.getDisk()).isBetween(10.0, 95.0);
            assertThat(m.getRam()).isBetween(20.0, 95.0);
            assertThat(m.getCpu()).isBetween(10.0, 95.0);
            assertThat(m.getLatencyMs()).isBetween(5, 150);
            assertThat(m.getNetworkInMbps()).isBetween(0.0, 500.0);
            assertThat(m.getNetworkOutMbps()).isBetween(0.0, 500.0);
            assertThat(m.getTimestamp()).isNotNull();
        }
    }

    @Test
    void autoGenerateGoesThroughSaveMetricSoStatusAndAlertsStillApply() {
        Device device = device(1L, "Web Server 01", DeviceStatus.ONLINE);
        device.setType("Server");
        when(deviceRepository.findAll()).thenReturn(List.of(device));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(statusCalculator.calculate(any(Device.class), any(Metric.class))).thenReturn(DeviceStatus.CRITICAL);
        stubSavesToEchoArguments();

        metricService.autoGenerateMetrics();

        verify(alertService).createAlert(same(device), eq(AlertSeverity.CRITICAL), anyString());
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.CRITICAL);
    }
}
