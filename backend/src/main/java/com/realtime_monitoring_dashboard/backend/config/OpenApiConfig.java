package com.realtime_monitoring_dashboard.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.realtime_monitoring_dashboard.backend.security.AgentApiKeyFilter;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String AGENT_KEY = "agentKey";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Real-time Monitoring Dashboard API")
                        .description("Log in via POST /api/auth/login, copy the token and paste it into Authorize (bearerAuth). "
                                + "Agent endpoints /api/devices/register and /api/devices/ingest use the X-Agent-Key header (agentKey).")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(AGENT_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AgentApiKeyFilter.HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
