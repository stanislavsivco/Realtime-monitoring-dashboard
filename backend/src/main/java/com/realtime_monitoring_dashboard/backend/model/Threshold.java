package com.realtime_monitoring_dashboard.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "thresholds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Threshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    @Column(nullable = false)
    private Double cpuWarning;

    @Column(nullable = false)
    private Double cpuCritical;

    @Column(nullable = false)
    private Double ramWarning;

    @Column(nullable = false)
    private Double ramCritical;

    @Column(nullable = false)
    private Double diskWarning;

    @Column(nullable = false)
    private Double diskCritical;

    @Column(name = "latency_warning_ms", nullable = false)
    private Integer latencyWarningMs;

    @Column(name = "latency_critical_ms", nullable = false)
    private Integer latencyCriticalMs;
}