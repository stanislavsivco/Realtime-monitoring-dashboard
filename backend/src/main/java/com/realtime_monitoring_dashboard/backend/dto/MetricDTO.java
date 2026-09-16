package com.realtime_monitoring_dashboard.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricDTO {
    private Long id;
    private Long deviceId;
    private LocalDateTime timestamp;
    private Double disk;
    private Double ram;
}