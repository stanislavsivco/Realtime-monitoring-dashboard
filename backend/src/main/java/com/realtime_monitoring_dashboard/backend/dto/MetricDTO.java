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
public class MetricDTO {
    private Long id;
    private Long deviceId;
    private Instant timestamp;
    private Double disk;
    private Double ram;
    private Double cpu;
    private Integer latencyMs;
    private Double networkInMbps;
    private Double networkOutMbps;
}