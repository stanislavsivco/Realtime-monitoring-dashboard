package com.realtime_monitoring_dashboard.backend.dto;

import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 
@AllArgsConstructor 
public class CreateDeviceRequestDTO {

    @NotBlank
    private String name;

    @NotBlank 
    private String type;

    @NotBlank 
    private String location;

    @NotNull 
    private DeviceStatus status;
    
}
