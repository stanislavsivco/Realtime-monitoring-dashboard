package com.realtime_monitoring_dashboard.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;
import com.realtime_monitoring_dashboard.backend.service.MetricService;
import com.realtime_monitoring_dashboard.backend.service.MetricSimulator;

class SimulationSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DeviceRepository.class, () -> mock(DeviceRepository.class))
            .withBean(MetricRepository.class, () -> mock(MetricRepository.class))
            .withBean(MetricService.class, () -> mock(MetricService.class))
            .withUserConfiguration(DataSeeder.class, MetricSimulator.class);

    @Test
    void demoDataIsOffWhenPropertyIsMissing() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DataSeeder.class);
            assertThat(context).doesNotHaveBean(MetricSimulator.class);
        });
    }

    @Test
    void demoDataIsOffWhenPropertyIsFalse() {
        runner.withPropertyValues("simulation.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DataSeeder.class);
            assertThat(context).doesNotHaveBean(MetricSimulator.class);
        });
    }

    @Test
    void demoDataIsOnWhenPropertyIsTrue() {
        runner.withPropertyValues("simulation.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(DataSeeder.class);
            assertThat(context).hasSingleBean(MetricSimulator.class);
        });
    }
}
