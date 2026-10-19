package com.realtime_monitoring_dashboard.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestMetricRequestDTO {

    @NotNull
    @Positive
    private Long deviceId;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double cpu;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double ram;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double disk;

    @PositiveOrZero
    private Integer latencyMs;

    @PositiveOrZero
    private Double networkInMbps;

    @PositiveOrZero
    private Double networkOutMbps;
}
