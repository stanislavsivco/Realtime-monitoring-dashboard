package com.realtime_monitoring_dashboard.backend.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

import com.realtime_monitoring_dashboard.backend.exception.ErrorResponse;
import com.realtime_monitoring_dashboard.backend.security.AgentApiKeyFilter;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String AGENT_KEY = "agentKey";
    static final String ERROR_SCHEMA = "ErrorResponse";

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

    @Bean
    public OperationCustomizer securityErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (requiresAuthentication(operation)) {
                addIfMissing(responses, "401", "Missing, invalid or expired credentials");
            }
            if (isAdminOnly(handlerMethod)) {
                addIfMissing(responses, "403", "Only ADMIN can perform this action");
            }
            return operation;
        };
    }

    @Bean
    public OpenApiCustomizer errorResponseSchema() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getResponses() != null) {
                    operation.getResponses().forEach((code, response) -> {
                        if (isError(code)) {
                            response.setContent(errorContent());
                        }
                    });
                }
            }));
        };
    }

    static boolean requiresAuthentication(Operation operation) {
        return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
    }

    static boolean isAdminOnly(HandlerMethod handlerMethod) {
        PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), PreAuthorize.class);
        if (preAuthorize == null) {
            preAuthorize = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
        }
        return preAuthorize != null && preAuthorize.value().contains("ADMIN");
    }

    static boolean isError(String code) {
        return code.length() == 3 && (code.charAt(0) == '4' || code.charAt(0) == '5');
    }

    private static void addIfMissing(ApiResponses responses, String code, String description) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse().description(description));
        }
    }

    private static Content errorContent() {
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)));
    }
}
