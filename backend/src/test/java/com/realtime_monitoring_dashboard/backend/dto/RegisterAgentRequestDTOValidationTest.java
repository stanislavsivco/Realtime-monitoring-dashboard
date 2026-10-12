package com.realtime_monitoring_dashboard.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RegisterAgentRequestDTOValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void hostnameOnlyIsValid() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("web-01.local", null, null);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void fullyPopulatedRequestIsValid() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("web-01.local", "Server", "DC1");

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void blankHostnameIsRejected() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("   ", "Server", "DC1");

        assertThat(validator.validate(dto)).hasSize(1);
    }

    @Test
    void nullHostnameIsRejected() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO(null, "Server", "DC1");

        assertThat(validator.validate(dto)).hasSize(1);
    }
}
