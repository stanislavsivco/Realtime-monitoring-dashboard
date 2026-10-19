package com.realtime_monitoring_dashboard.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateDeviceRequestDTOValidationTest {

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

    private static Set<String> invalidProperties(CreateDeviceRequestDTO dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestHasNoViolations() {
        CreateDeviceRequestDTO dto = new CreateDeviceRequestDTO("Server-1", "Server", "Kosice", DeviceStatus.ONLINE);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void emptyRequestViolatesEveryField() {
        assertThat(invalidProperties(new CreateDeviceRequestDTO()))
                .containsExactlyInAnyOrder("name", "type", "location", "status");
    }

    @Test
    void blankStringsAreRejected() {
        CreateDeviceRequestDTO dto = new CreateDeviceRequestDTO("   ", "", " ", DeviceStatus.ONLINE);

        assertThat(invalidProperties(dto)).containsExactlyInAnyOrder("name", "type", "location");
    }

    @Test
    void missingStatusIsRejected() {
        CreateDeviceRequestDTO dto = new CreateDeviceRequestDTO("Server-1", "Server", "Kosice", null);

        assertThat(invalidProperties(dto)).containsExactly("status");
    }

    @Test
    void valuesLongerThanColumnAreRejectedAndMaxLengthIsAccepted() {
        String tooLong = "x".repeat(256);
        String maxLength = "x".repeat(255);

        assertThat(invalidProperties(new CreateDeviceRequestDTO(tooLong, tooLong, tooLong, DeviceStatus.ONLINE)))
                .containsExactlyInAnyOrder("name", "type", "location");
        assertThat(validator.validate(new CreateDeviceRequestDTO(maxLength, maxLength, maxLength, DeviceStatus.ONLINE)))
                .isEmpty();
    }
}
