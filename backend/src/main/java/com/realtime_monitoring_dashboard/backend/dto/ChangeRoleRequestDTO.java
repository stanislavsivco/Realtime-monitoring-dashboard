package com.realtime_monitoring_dashboard.backend.dto;

import com.realtime_monitoring_dashboard.backend.model.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRoleRequestDTO {

    @NotNull
    private Role role;
}
