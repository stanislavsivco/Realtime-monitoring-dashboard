package com.realtime_monitoring_dashboard.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterAgentRequestDTO {

    @NotBlank
    private String hostname;

    private String type;

    private String location;
}