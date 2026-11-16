package com.realtime_monitoring_dashboard.backend.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedDeviceDTO {
    private Long id;
    private String deviceName;
    private Instant blockedAt;
}
