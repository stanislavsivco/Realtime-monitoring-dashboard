package com.realtime_monitoring_dashboard.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RegisterAgentRequestDTOValidationTest {

    private static final String MACHINE_ID = "0123456789abcdef".repeat(4);

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
    void hostnameAndMachineIdAreEnough() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("web-01.local", null, null, MACHINE_ID);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void fullyPopulatedRequestIsValid() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("web-01.local", "Server", "DC1", MACHINE_ID);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void blankHostnameIsRejected() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("   ", "Server", "DC1", MACHINE_ID);

        assertThat(validator.validate(dto)).hasSize(1);
    }

    @Test
    void nullHostnameIsRejected() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO(null, "Server", "DC1", MACHINE_ID);

        assertThat(validator.validate(dto)).hasSize(1);
    }

    @Test
    void valuesLongerThanColumnAreRejected() {
        String tooLong = "x".repeat(256);

        assertThat(validator.validate(new RegisterAgentRequestDTO(tooLong, tooLong, tooLong, MACHINE_ID))).hasSize(3);
        assertThat(validator.validate(new RegisterAgentRequestDTO("x".repeat(255), null, null, MACHINE_ID))).isEmpty();
    }

    @Test
    void missingMachineIdIsRejected() {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("web-01.local", null, null, null);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("machineId");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "0123456789abcdef",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeg",
            " 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde\n"
    })
    void machineIdThatIsNotLowercaseSha256HexIsRejected(String machineId) {
        RegisterAgentRequestDTO dto = new RegisterAgentRequestDTO("web-01.local", null, null, machineId);

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("machineId");
    }
}
