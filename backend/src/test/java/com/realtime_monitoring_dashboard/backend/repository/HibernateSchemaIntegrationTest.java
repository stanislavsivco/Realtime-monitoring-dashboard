package com.realtime_monitoring_dashboard.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.realtime_monitoring_dashboard.backend.support.PostgresTestDatabase;
import com.realtime_monitoring_dashboard.backend.support.SchemaInspector;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HibernateSchemaIntegrationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    private DataSource dataSource;

    private SchemaInspector schema;

    @BeforeEach
    void setUp() {
        schema = new SchemaInspector(dataSource);
    }

    @Test
    void metricsTableHasTimestampIndex() {
        assertThat(schema.indexColumns("idx_metrics_timestamp")).containsExactly("timestamp");
    }

    @Test
    void metricsTableHasDeviceAndTimestampIndexInThatOrder() {
        assertThat(schema.indexColumns("idx_metrics_device_timestamp")).containsExactly("device_id", "timestamp");
    }

    @Test
    void alertsTableHasDeviceAndResolvedIndexInThatOrder() {
        assertThat(schema.indexColumns("idx_alerts_device_resolved")).containsExactly("device_id", "resolved");
    }

    @Test
    void usernameDeviceNameAndThresholdDeviceAreUnique() {
        assertThat(schema.uniqueConstraintCount("users", "username")).isEqualTo(1);
        assertThat(schema.uniqueConstraintCount("devices", "name")).isEqualTo(1);
        assertThat(schema.uniqueConstraintCount("thresholds", "device_id")).isEqualTo(1);
    }

    @Test
    void tokenVersionHasDefaultZeroAndIsNotNullable() {
        assertThat(schema.columnDefault("users", "token_version")).isEqualTo("0");
        assertThat(schema.isNullable("users", "token_version")).isFalse();
    }

    @Test
    void simulatedHasDefaultTrueAndIsNotNullable() {
        assertThat(schema.columnDefault("devices", "simulated")).isEqualTo("true");
        assertThat(schema.isNullable("devices", "simulated")).isFalse();
    }

    @Test
    void rowInsertedWithoutDefaultedColumnsGetsTheDefaults() {
        schema.jdbc().update("insert into users (username, password, role) values ('raw-user', 'x', 'VIEWER')");
        schema.jdbc().update("insert into devices (name) values ('raw-device')");

        Integer tokenVersion = schema.jdbc().queryForObject(
                "select token_version from users where username = 'raw-user'", Integer.class);
        Boolean simulated = schema.jdbc().queryForObject(
                "select simulated from devices where name = 'raw-device'", Boolean.class);

        assertThat(tokenVersion).isZero();
        assertThat(simulated).isTrue();
    }
}