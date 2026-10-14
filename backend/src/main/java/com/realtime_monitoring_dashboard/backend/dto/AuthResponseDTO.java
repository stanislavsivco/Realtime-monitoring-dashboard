package com.realtime_monitoring_dashboard.backend.dto;

import com.realtime_monitoring_dashboard.backend.model.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDTO {
    @ToString.Exclude
    private String token;
    private String username;
    private Role role;
}
