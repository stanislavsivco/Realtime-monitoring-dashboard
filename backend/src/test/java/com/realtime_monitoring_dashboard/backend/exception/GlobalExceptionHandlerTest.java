package com.realtime_monitoring_dashboard.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolationException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest request =
            new ServletWebRequest(new MockHttpServletRequest("GET", "/api/devices/5"));

    @Test
    void resourceNotFoundBecomes404WithMessageAndPath() {
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(
                new ResourceNotFoundException("Device with ID 5 does not exist"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getError()).isEqualTo("Not Found");
        assertThat(body.getMessage()).isEqualTo("Device with ID 5 does not exist");
        assertThat(body.getPath()).isEqualTo("/api/devices/5");
        assertThat(body.getTimestamp()).isNotNull();
        assertThat(body.getFieldErrors()).isNull();
    }

    @Test
    void constraintViolationBecomes400() {
        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(
                new ConstraintViolationException("getDeviceByID.id: must be greater than 0", Set.of()), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("getDeviceByID.id: must be greater than 0");
    }

    @Test
    void unexpectedExceptionBecomes500WithoutLeakingInternalMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("connection string jdbc:postgresql://secret-host"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        ErrorResponse body = response.getBody();
        assertThat(body.getMessage()).doesNotContain("secret-host");
        assertThat(body.getPath()).isEqualTo("/api/devices/5");
    }
}
