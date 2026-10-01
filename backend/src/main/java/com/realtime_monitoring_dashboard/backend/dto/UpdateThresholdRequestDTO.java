package com.realtime_monitoring_dashboard.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateThresholdRequestDTO {

    @NotNull
    @Positive
    private Double cpuWarning;

    @NotNull
    @Positive
    private Double cpuCritical;

    @NotNull
    @Positive
    private Double ramWarning;

    @NotNull
    @Positive
    private Double ramCritical;

    @NotNull
    @Positive
    private Double diskWarning;

    @NotNull
    @Positive
    private Double diskCritical;

    @NotNull
    @Positive
    private Integer latencyWarningMs;

    @NotNull
    @Positive
    private Integer latencyCriticalMs;
}