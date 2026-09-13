package com.realtime_monitoring_dashboard.backend.dto;

import com.realtime_monitoring_dashboard.backend.model.DeviceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceDTO {
    private Long id;
    private String name;
    private String type;
    private String location;
    private DeviceStatus status;
}