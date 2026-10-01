package com.realtime_monitoring_dashboard.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThresholdDTO {
    private Long id;
    private Long deviceId;
    private Double cpuWarning;
    private Double cpuCritical;
    private Double ramWarning;
    private Double ramCritical;
    private Double diskWarning;
    private Double diskCritical;
    private Integer latencyWarningMs;
    private Integer latencyCriticalMs;
}