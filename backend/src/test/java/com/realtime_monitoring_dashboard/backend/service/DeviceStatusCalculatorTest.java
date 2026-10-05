package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.model.Metric;

class DeviceStatusCalculatorTest {

    private final DeviceStatusCalculator calculator = new DeviceStatusCalculator();

    private static Metric metric(double cpu, double ram, double disk, int latency, double netIn, double netOut) {
        return Metric.builder().cpu(cpu).ram(ram).disk(disk).latencyMs(latency)
                .networkInMbps(netIn).networkOutMbps(netOut).build();
    }

    @ParameterizedTest(name = "cpu={0} ram={1} disk={2} latency={3} in={4} out={5} -> {6}")
    @CsvSource({
            "50,50,50,100,100,100,ONLINE",
            "75.0,50,50,100,100,100,ONLINE",
            "75.1,50,50,100,100,100,WARNING",
            "50,75.1,50,100,100,100,WARNING",
            "50,50,75.1,100,100,100,WARNING",
            "50,50,50,200,100,100,ONLINE",
            "50,50,50,201,100,100,WARNING",
            "50,50,50,100,800.0,100,ONLINE",
            "50,50,50,100,800.1,100,WARNING",
            "50,50,50,100,100,800.1,WARNING",
            "90.0,50,50,100,100,100,WARNING",
            "90.1,50,50,100,100,100,CRITICAL",
            "50,90.1,50,100,100,100,CRITICAL",
            "50,50,90.1,100,100,100,CRITICAL",
            "50,50,50,500,100,100,WARNING",
            "50,50,50,501,100,100,CRITICAL",
            "50,50,50,100,950.0,100,WARNING",
            "50,50,50,100,950.1,100,CRITICAL",
            "50,50,50,100,100,950.1,CRITICAL"
    })
    void calculatesExpectedStatus(double cpu, double ram, double disk, int latency,
                                  double netIn, double netOut, DeviceStatus expected) {
        assertThat(calculator.calculate(metric(cpu, ram, disk, latency, netIn, netOut))).isEqualTo(expected);
    }

    @Test
    void nullFieldsAreTreatedAsHealthy() {
        Metric metric = Metric.builder().build();

        assertThat(calculator.calculate(metric)).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void oneCriticalFieldIsEnoughEvenWithOthersHealthy() {
        Metric metric = Metric.builder().cpu(95.0).ram(10.0).disk(10.0).latencyMs(10)
                .networkInMbps(10.0).networkOutMbps(10.0).build();

        assertThat(calculator.calculate(metric)).isEqualTo(DeviceStatus.CRITICAL);
    }
}
