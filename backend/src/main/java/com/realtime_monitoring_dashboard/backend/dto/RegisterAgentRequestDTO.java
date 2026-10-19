package com.realtime_monitoring_dashboard.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterAgentRequestDTO {

    @NotBlank
    @Size(max = 255)
    private String hostname;

    @Size(max = 255)
    private String type;

    @Size(max = 255)
    private String location;
}
