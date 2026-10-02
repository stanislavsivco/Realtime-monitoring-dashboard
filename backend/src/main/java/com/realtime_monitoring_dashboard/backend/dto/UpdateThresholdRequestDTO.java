package com.realtime_monitoring_dashboard.backend.dto;

import jakarta.validation.constraints.DecimalMax;
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
    @DecimalMax("100.0")
    private Double cpuWarning;

    @NotNull
    @Positive
    @DecimalMax("100.0")
    private Double cpuCritical;

    @NotNull
    @Positive
    @DecimalMax("100.0")
    private Double ramWarning;

    @NotNull
    @Positive
    @DecimalMax("100.0")
    private Double ramCritical;

    @NotNull
    @Positive
    @DecimalMax("100.0")
    private Double diskWarning;

    @NotNull
    @Positive
    @DecimalMax("100.0")
    private Double diskCritical;

    @NotNull
    @Positive
    private Integer latencyWarningMs;

    @NotNull
    @Positive
    private Integer latencyCriticalMs;
}