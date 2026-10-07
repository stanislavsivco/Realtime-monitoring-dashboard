package com.realtime_monitoring_dashboard.backend.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.ColumnDefault;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String type;
    private String location;

    @Enumerated(EnumType.STRING)
    private DeviceStatus status;

    @Column(nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean simulated = true;

    @OneToMany (mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Metric> metrics = new ArrayList<>();
}