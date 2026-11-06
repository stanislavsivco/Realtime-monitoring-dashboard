package com.realtime_monitoring_dashboard.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestDatabase {

    private static final String IMAGE = "postgres:18.6";
    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(IMAGE);
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    static {
        CONTAINER.start();
    }

    private PostgresTestDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        String url = createDatabase();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static String createDatabase() {
        String name = "it_" + DATABASE_COUNTER.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create test database " + name, e);
        }
        return "jdbc:postgresql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5432) + "/" + name;
    }
}