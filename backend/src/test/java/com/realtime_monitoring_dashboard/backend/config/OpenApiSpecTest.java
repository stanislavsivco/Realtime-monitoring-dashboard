package com.realtime_monitoring_dashboard.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=openapi-test-secret-that-is-long-enough",
        "agent.api-key=openapi-test-agent-key-that-is-long-enough",
        "admin.password="
})
class OpenApiSpecTest {

    private static final Path SPEC_FILE = Path.of("..", "docs", "openapi.json");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final Set<String> ADMIN_ONLY = Set.of(
            "createDevice", "updateDevice", "deleteDevice", "updateThresholds",
            "acknowledgeAlert", "resolveAlert", "getUsers", "changeUserRole");
    private static final Set<String> PUBLIC = Set.of("login", "register");

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Autowired
    private WebApplicationContext context;

    private Map<String, Object> spec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void loadSpec() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String body = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        spec = jsonMapper.readValue(body, Map.class);
        spec.remove("servers");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> operations() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        paths.values().forEach(path -> ((Map<String, Object>) path).forEach((method, operation) -> {
            if (HTTP_METHODS.contains(method)) {
                result.add((Map<String, Object>) operation);
            }
        }));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> responses(Map<String, Object> operation) {
        return (Map<String, Map<String, Object>>) operation.get("responses");
    }

    private Map<String, Object> operation(String operationId) {
        return operations().stream()
                .filter(operation -> operationId.equals(operation.get("operationId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No operation " + operationId));
    }

    @Test
    void everyOperationHasAUniqueOperationId() {
        List<Object> ids = operations().stream().map(operation -> operation.get("operationId")).toList();

        assertThat(ids).hasSize(19).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(ids).allSatisfy(id -> assertThat((String) id).matches("[a-z][A-Za-z]+"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyErrorResponseUsesErrorResponseSchema() {
        assertThat(((Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas")))
                .containsKey(OpenApiConfig.ERROR_SCHEMA);

        for (Map<String, Object> operation : operations()) {
            responses(operation).forEach((code, response) -> {
                if (OpenApiConfig.isError(code)) {
                    assertThat(response.toString())
                            .as("%s %s", operation.get("operationId"), code)
                            .contains("#/components/schemas/" + OpenApiConfig.ERROR_SCHEMA);
                }
            });
        }
    }

    @Test
    void adminOnlyOperationsDocument403AndOthersDoNot() {
        for (Map<String, Object> operation : operations()) {
            String id = (String) operation.get("operationId");
            assertThat(responses(operation).containsKey("403")).as(id).isEqualTo(ADMIN_ONLY.contains(id));
        }
    }

    @Test
    void securedOperationsDocument401() {
        for (Map<String, Object> operation : operations()) {
            String id = (String) operation.get("operationId");
            if (!PUBLIC.contains(id)) {
                assertThat(responses(operation)).as(id).containsKey("401");
            }
        }
        assertThat(responses(operation("register"))).doesNotContainKey("401");
    }

    @Test
    void loginAndRegisterArePublicAndDocument429() {
        for (String id : PUBLIC) {
            Map<String, Object> operation = operation(id);
            assertThat(operation.get("security")).as(id).isEqualTo(List.of());
            assertThat(responses(operation)).as(id).containsKey("429");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagedOperationsExposePageSizeAndSortAsQueryParameters() {
        for (String id : List.of("getAlerts", "getMetricHistory")) {
            List<Object> names = ((List<Map<String, Object>>) operation(id).get("parameters")).stream()
                    .map(parameter -> parameter.get("name"))
                    .toList();
            assertThat(names).as(id).contains("page", "size", "sort").doesNotContain("pageable");
        }
    }

    @Test
    void successResponsesAreJson() {
        assertThat(responses(operation("getDevice")).get("200").toString()).contains("application/json").doesNotContain("*/*");
    }

    @Test
    void createDeviceDocuments201() {
        assertThat(responses(operation("createDevice"))).containsKey("201").doesNotContainKey("200");
    }

    @Test
    void committedSpecFileMatchesTheApi() throws IOException {
        String current = jsonMapper.writeValueAsString(spec).replace("\r\n", "\n") + "\n";

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SPEC_FILE.getParent());
            Files.writeString(SPEC_FILE, current, StandardCharsets.UTF_8);
        }

        assertThat(SPEC_FILE)
                .as("docs/openapi.json is missing or outdated, run: ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true")
                .exists();
        String committed = Files.readString(SPEC_FILE, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(committed)
                .as("docs/openapi.json is outdated, run: ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true")
                .isEqualTo(current);
    }
}
