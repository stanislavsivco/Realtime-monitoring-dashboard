package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.DeviceBlockedException;
import com.realtime_monitoring_dashboard.backend.exception.DeviceNameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.model.Threshold;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import com.realtime_monitoring_dashboard.backend.repository.BlockedDeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import com.realtime_monitoring_dashboard.backend.repository.ThresholdRepository;
import com.realtime_monitoring_dashboard.backend.support.PostgresTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DeviceService.class)
class DeviceServiceIntegrationTest {

    private static final String MACHINE_ID = "0123456789abcdef".repeat(4);
    private static final String OTHER_MACHINE_ID = "fedcba9876543210".repeat(4);

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    private DeviceService deviceService;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private ThresholdRepository thresholdRepository;
    @Autowired
    private BlockedDeviceRepository blockedDeviceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Device deviceWithMetricAlertAndThreshold(String name) {
        Device device = deviceRepository.save(Device.builder()
                .name(name).type("Server").location("DC1").status(DeviceStatus.CRITICAL).build());

        metricRepository.save(Metric.builder()
                .device(device).timestamp(LocalDateTime.now()).cpu(95.0).ram(50.0).disk(40.0).build());
        alertRepository.save(Alert.builder()
                .device(device).severity(AlertSeverity.CRITICAL).message("CPU high")
                .timestamp(LocalDateTime.now()).resolved(false).build());
        thresholdRepository.save(Threshold.builder()
                .device(device).cpuWarning(70.0).cpuCritical(90.0).ramWarning(70.0).ramCritical(90.0)
                .diskWarning(70.0).diskCritical(90.0).latencyWarningMs(100).latencyCriticalMs(200).build());
        entityManager.flush();
        entityManager.clear();
        return device;
    }

    @Test
    void deletingDeviceDirectlyFailsWhileAlertsAndThresholdsReferenceIt() {
        Device device = deviceWithMetricAlertAndThreshold("plain-delete");

        assertThrows(DataIntegrityViolationException.class, () -> {
            deviceRepository.deleteById(device.getId());
            deviceRepository.flush();
        });
    }

    @Test
    void deleteDeviceRemovesDeviceWithMetricsAlertsAndThreshold() {
        Device device = deviceWithMetricAlertAndThreshold("service-delete");
        Long id = device.getId();

        deviceService.deleteDevice(id);

        assertThat(deviceRepository.existsById(id)).isFalse();
        assertThat(metricRepository.findTopByDeviceIdOrderByTimestampDesc(id)).isEmpty();
        assertThat(alertRepository.findByDeviceIdAndResolvedFalse(id)).isEmpty();
        assertThat(thresholdRepository.findByDeviceId(id)).isEmpty();
    }

    @Test
    void deleteDeviceKeepsDataOfOtherDevices() {
        Device toDelete = deviceWithMetricAlertAndThreshold("delete-me");
        Device toKeep = deviceWithMetricAlertAndThreshold("keep-me");

        deviceService.deleteDevice(toDelete.getId());

        assertThat(deviceRepository.existsById(toKeep.getId())).isTrue();
        assertThat(metricRepository.findTopByDeviceIdOrderByTimestampDesc(toKeep.getId())).isPresent();
        assertThat(alertRepository.findByDeviceIdAndResolvedFalse(toKeep.getId())).hasSize(1);
        assertThat(thresholdRepository.findByDeviceId(toKeep.getId())).isPresent();
    }

    @Test
    void databaseRejectsDuplicateDeviceName() {
        deviceService.createDevice(new CreateDeviceRequestDTO("unique-name", "Server", "DC1"));

        assertThrows(DeviceNameAlreadyExistsException.class, () -> deviceService.createDevice(
                new CreateDeviceRequestDTO("unique-name", "Server", "DC2")));
        assertThrows(DataIntegrityViolationException.class, () -> deviceRepository.saveAndFlush(Device.builder()
                .name("unique-name").type("Server").location("DC3").status(DeviceStatus.ONLINE).build()));
    }

    @Test
    void registerAgentTwiceReturnsSameDevice() {
        Long first = deviceService.registerAgent(agent("agent-host", MACHINE_ID)).getId();
        Long second = deviceService.registerAgent(agent("agent-host", MACHINE_ID)).getId();

        assertThat(second).isEqualTo(first);
        assertThat(deviceRepository.findAll()).hasSize(1);
    }

    @Test
    void deletedAgentDeviceStaysDeletedUntilAdminUnblocksIt() {
        Long id = deviceService.registerAgent(agent("agent-host", MACHINE_ID)).getId();

        deviceService.deleteDevice(id);

        assertThrows(DeviceBlockedException.class, () -> deviceService.registerAgent(agent("agent-host", MACHINE_ID)));
        assertThrows(DeviceBlockedException.class, () -> deviceService.registerAgent(agent("new-name", MACHINE_ID)));
        assertThat(deviceRepository.findAll()).isEmpty();
        assertThat(deviceService.getBlockedDevices()).singleElement()
                .satisfies(blocked -> assertThat(blocked.getDeviceName()).isEqualTo("agent-host"));

        deviceService.unblockDevice(deviceService.getBlockedDevices().get(0).getId());
        Long again = deviceService.registerAgent(agent("agent-host", MACHINE_ID)).getId();

        assertThat(again).isNotEqualTo(id);
        assertThat(blockedDeviceRepository.findAll()).isEmpty();
    }

    @Test
    void blockingOneMachineDoesNotAffectOthers() {
        Long blocked = deviceService.registerAgent(agent("blocked-host", MACHINE_ID)).getId();
        deviceService.registerAgent(agent("other-host", OTHER_MACHINE_ID));

        deviceService.deleteDevice(blocked);

        assertThat(deviceService.registerAgent(agent("other-host", OTHER_MACHINE_ID)).getName()).isEqualTo("other-host");
    }

    @Test
    void deletingDeviceWithoutMachineIdBlocksNothing() {
        Device manual = deviceWithMetricAlertAndThreshold("manual-device");

        deviceService.deleteDevice(manual.getId());

        assertThat(blockedDeviceRepository.findAll()).isEmpty();
    }

    @Test
    void agentDeviceRenamedByAdminIsFoundByMachineIdInsteadOfDuplicated() {
        Long id = deviceService.registerAgent(agent("agent-host", MACHINE_ID)).getId();
        deviceService.updateDevice(id, new CreateDeviceRequestDTO("Reception PC", "Windows", "Kosice"));

        DeviceDTO again = deviceService.registerAgent(agent("agent-host", MACHINE_ID));

        assertThat(again.getId()).isEqualTo(id);
        assertThat(again.getName()).isEqualTo("Reception PC");
        assertThat(deviceRepository.findAll()).hasSize(1);
    }

    @Test
    void deviceRegisteredBeforeMachineIdsExistedIsAdoptedByName() {
        Device legacy = deviceRepository.saveAndFlush(Device.builder()
                .name("legacy-host").type("Windows").location("Unknown").status(DeviceStatus.ONLINE)
                .simulated(false).build());

        Long id = deviceService.registerAgent(agent("legacy-host", MACHINE_ID)).getId();

        assertThat(id).isEqualTo(legacy.getId());
        assertThat(deviceRepository.findByMachineIdHash(MACHINE_ID)).get()
                .extracting(Device::getId).isEqualTo(legacy.getId());
        assertThat(deviceRepository.findAll()).hasSize(1);
    }

    @Test
    void agentCannotTakeOverNameOfAnotherMachine() {
        deviceService.registerAgent(agent("shared-name", MACHINE_ID));

        assertThrows(DeviceNameAlreadyExistsException.class,
                () -> deviceService.registerAgent(agent("shared-name", OTHER_MACHINE_ID)));
        assertThat(deviceRepository.findAll()).hasSize(1);
    }

    @Test
    void databaseRejectsTwoDevicesWithSameMachineId() {
        deviceRepository.saveAndFlush(Device.builder()
                .name("first").status(DeviceStatus.ONLINE).machineIdHash(MACHINE_ID).build());

        assertThrows(DataIntegrityViolationException.class, () -> deviceRepository.saveAndFlush(Device.builder()
                .name("second").status(DeviceStatus.ONLINE).machineIdHash(MACHINE_ID).build()));
    }

    private static RegisterAgentRequestDTO agent(String hostname, String machineId) {
        return new RegisterAgentRequestDTO(hostname, null, null, machineId);
    }
}