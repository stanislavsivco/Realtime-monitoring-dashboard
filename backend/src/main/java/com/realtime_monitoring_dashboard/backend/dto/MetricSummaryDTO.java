package com.realtime_monitoring_dashboard.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricSummaryDTO {
    private Double avgCpu;
    private Double maxCpu;
    private Double minCpu;
    private Double avgRam;
    private Double maxRam;
    private Double minRam;
    private Double avgDisk;
    private Double maxDisk;
    private Double minDisk;
    private Double avgLatencyMs;
}
