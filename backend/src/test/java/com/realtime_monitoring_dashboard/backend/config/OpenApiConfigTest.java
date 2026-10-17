package com.realtime_monitoring_dashboard.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.realtime_monitoring_dashboard.backend.controller.AuthController;
import com.realtime_monitoring_dashboard.backend.controller.DeviceController;
import com.realtime_monitoring_dashboard.backend.controller.MetricController;
import com.realtime_monitoring_dashboard.backend.dto.MetricDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

class OpenApiConfigTest {

    private final OpenAPI openApi = new OpenApiConfig().openApi();

    @Test
    void bearerSchemeUsesJwtInAuthorizationHeader() {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH);

        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    void agentKeySchemeUsesSameHeaderAsFilter() {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.AGENT_KEY);

        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
        assertThat(scheme.getIn()).isEqualTo(SecurityScheme.In.HEADER);
        assertThat(scheme.getName()).isEqualTo("X-Agent-Key");
    }

    @Test
    void bearerIsRequiredGloballyByDefault() {
        assertThat(openApi.getSecurity()).hasSize(1);
        assertThat(openApi.getSecurity().get(0)).containsKey(OpenApiConfig.BEARER_AUTH);
    }

    @Test
    void authEndpointsDoNotRequireToken() {
        SecurityRequirements requirements = AuthController.class.getAnnotation(SecurityRequirements.class);

        assertThat(requirements).isNotNull();
        assertThat(requirements.value()).isEmpty();
    }

    @Test
    void agentEndpointsRequireAgentKey() throws NoSuchMethodException {
        Method register = DeviceController.class.getMethod("registerAgent", RegisterAgentRequestDTO.class);
        Method ingest = MetricController.class.getMethod("ingestMetric", MetricDTO.class);

        assertThat(register.getAnnotation(SecurityRequirement.class).name()).isEqualTo(OpenApiConfig.AGENT_KEY);
        assertThat(ingest.getAnnotation(SecurityRequirement.class).name()).isEqualTo(OpenApiConfig.AGENT_KEY);
    }
}
