package com.realtime_monitoring_dashboard.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.realtime_monitoring_dashboard.backend.dto.CreateDeviceRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.DeviceDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.GlobalExceptionHandler;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import com.realtime_monitoring_dashboard.backend.service.DeviceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceControllerTest {

    private static final String VALID_BODY = """
            {"name": "Server-1", "type": "Server", "location": "Kosice", "status": "ONLINE"}
            """;

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private DeviceController deviceController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(deviceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    private static DeviceDTO dto(Long id, String name) {
        return DeviceDTO.builder().id(id).name(name).type("Server").location("Kosice")
                .status(DeviceStatus.ONLINE).build();
    }

    @Test
    void getAllDevicesReturnsJsonArray() throws Exception {
        when(deviceService.getAllDevices()).thenReturn(List.of(dto(1L, "A"), dto(2L, "B")));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[1].name").value("B"));
    }

    @Test
    void getDeviceByIdReturnsDevice() throws Exception {
        when(deviceService.getDeviceById(1L)).thenReturn(dto(1L, "Server-1"));

        mockMvc.perform(get("/api/devices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Server-1"));
    }

    @Test
    void getDeviceByIdReturns404JsonWhenMissing() throws Exception {
        when(deviceService.getDeviceById(99L))
                .thenThrow(new ResourceNotFoundException("Device with ID 99 does not exist"));

        mockMvc.perform(get("/api/devices/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Device with ID 99 does not exist"));
    }

    @Test
    void getDeviceByIdWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/devices/abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deviceService);
    }

    @Test
    void getDeviceByIdWithNegativeIdReturns400() throws Exception {
        mockMvc.perform(get("/api/devices/-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deviceService);
    }

    @Test
    void createDevicePassesRequestToServiceAndReturnsDevice() throws Exception {
        when(deviceService.createDevice(any(CreateDeviceRequestDTO.class))).thenReturn(dto(10L, "Server-1"));

        mockMvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(deviceService).createDevice(any(CreateDeviceRequestDTO.class));
    }

    @Test
    void createDeviceWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        String invalid = """
                {"name": "", "type": "", "location": "   ", "status": null}
                """;

        mockMvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.location").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());

        verifyNoInteractions(deviceService);
    }

    @Test
    void createDeviceWithUnknownStatusReturns400() throws Exception {
        String body = """
                {"name": "n", "type": "t", "location": "l", "status": "BROKEN"}
                """;

        mockMvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deviceService);
    }

    @Test
    void createDeviceWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deviceService);
    }

    @Test
    void updateDeviceReturnsUpdatedDevice() throws Exception {
        when(deviceService.updateDevice(any(Long.class), any(CreateDeviceRequestDTO.class)))
                .thenReturn(dto(5L, "Server-1"));

        mockMvc.perform(put("/api/devices/5").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void updateDeviceReturns404WhenMissing() throws Exception {
        when(deviceService.updateDevice(any(Long.class), any(CreateDeviceRequestDTO.class)))
                .thenThrow(new ResourceNotFoundException("Device with ID 99 does not exist"));

        mockMvc.perform(put("/api/devices/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDeviceReturns204() throws Exception {
        mockMvc.perform(delete("/api/devices/3"))
                .andExpect(status().isNoContent());

        verify(deviceService).deleteDevice(3L);
    }

    @Test
    void registerAgentReturnsDevice() throws Exception {
        when(deviceService.registerAgent(any(RegisterAgentRequestDTO.class))).thenReturn(dto(7L, "agent-01"));
        String body = """
                {"hostname": "agent-01", "type": "Server", "location": "DC1"}
                """;

        mockMvc.perform(post("/api/devices/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(deviceService).registerAgent(any(RegisterAgentRequestDTO.class));
    }

    @Test
    void registerAgentWithBlankHostnameReturns400() throws Exception {
        String body = """
                {"hostname": "  "}
                """;

        mockMvc.perform(post("/api/devices/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hostname").exists());

        verifyNoInteractions(deviceService);
    }

    @Test
    void registerAgentWithoutTypeOrLocationIsAccepted() throws Exception {
        when(deviceService.registerAgent(any(RegisterAgentRequestDTO.class))).thenReturn(dto(8L, "bare-agent"));
        String body = """
                {"hostname": "bare-agent"}
                """;

        mockMvc.perform(post("/api/devices/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void unexpectedServiceErrorReturnsGeneric500WithoutLeakingDetails() throws Exception {
        when(deviceService.getAllDevices()).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred on the server"));
    }
}
