package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;
import com.realtime_monitoring_dashboard.backend.model.Threshold;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceStatusCalculatorTest {

    @Mock
    private ThresholdService thresholdService;

    @InjectMocks
    private DeviceStatusCalculator calculator;

    private static Device device(Long id) {
        return Device.builder().id(id).name("Server-1").type("Server").location("DC1").build();
    }

    private static Metric metric(double cpu, double ram, double disk, int latency) {
        return Metric.builder().cpu(cpu).ram(ram).disk(disk).latencyMs(latency).build();
    }

    private static Threshold defaultThreshold() {
        return Threshold.builder()
                .cpuWarning(75.0).cpuCritical(90.0)
                .ramWarning(75.0).ramCritical(90.0)
                .diskWarning(75.0).diskCritical(90.0)
                .latencyWarningMs(200).latencyCriticalMs(500)
                .build();
    }

    private void stubDefaultThresholdFor(Long deviceId) {
        when(thresholdService.getOrCreateDefaultForDevice(deviceId)).thenReturn(defaultThreshold());
    }

    @ParameterizedTest(name = "cpu={0} ram={1} disk={2} latency={3} -> {4}")
    @CsvSource({
            "50,50,50,100,ONLINE",
            "75.0,50,50,100,ONLINE",
            "75.1,50,50,100,WARNING",
            "50,75.1,50,100,WARNING",
            "50,50,75.1,100,WARNING",
            "50,50,50,200,ONLINE",
            "50,50,50,201,WARNING",
            "90.0,50,50,100,WARNING",
            "90.1,50,50,100,CRITICAL",
            "50,90.1,50,100,CRITICAL",
            "50,50,90.1,100,CRITICAL",
            "50,50,50,500,WARNING",
            "50,50,50,501,CRITICAL"
    })
    void calculatesExpectedStatus(double cpu, double ram, double disk, int latency, DeviceStatus expected) {
        Device device = device(1L);
        stubDefaultThresholdFor(1L);

        assertThat(calculator.calculate(device, metric(cpu, ram, disk, latency))).isEqualTo(expected);
    }

    @Test
    void nullMetricFieldsAreTreatedAsHealthy() {
        Device device = device(1L);
        stubDefaultThresholdFor(1L);

        assertThat(calculator.calculate(device, Metric.builder().build())).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void oneCriticalFieldIsEnoughEvenWithOthersHealthy() {
        Device device = device(1L);
        stubDefaultThresholdFor(1L);
        Metric metric = metric(95.0, 10.0, 10.0, 10);

        assertThat(calculator.calculate(device, metric)).isEqualTo(DeviceStatus.CRITICAL);
    }

    @Test
    void looksUpThresholdForTheGivenDeviceId() {
        Device device = device(42L);
        stubDefaultThresholdFor(42L);

        calculator.calculate(device, metric(10, 10, 10, 10));

        verify(thresholdService).getOrCreateDefaultForDevice(42L);
    }

    @Test
    void customThresholdsOverrideTheDefaults() {
        Device device = device(1L);
        Threshold strict = Threshold.builder()
                .cpuWarning(40.0).cpuCritical(60.0)
                .ramWarning(75.0).ramCritical(90.0)
                .diskWarning(75.0).diskCritical(90.0)
                .latencyWarningMs(200).latencyCriticalMs(500)
                .build();
        when(thresholdService.getOrCreateDefaultForDevice(1L)).thenReturn(strict);

        assertThat(calculator.calculate(device, metric(65.0, 10, 10, 10))).isEqualTo(DeviceStatus.CRITICAL);
    }

    @Test
    void networkThroughputNoLongerAffectsStatus() {
        Device device = device(1L);
        stubDefaultThresholdFor(1L);
        Metric metric = Metric.builder().cpu(10.0).ram(10.0).disk(10.0).latencyMs(10)
                .networkInMbps(10000.0).networkOutMbps(10000.0).build();

        assertThat(calculator.calculate(device, metric)).isEqualTo(DeviceStatus.ONLINE);
    }
}