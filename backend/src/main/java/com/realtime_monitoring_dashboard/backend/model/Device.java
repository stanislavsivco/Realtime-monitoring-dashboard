package com.realtime_monitoring_dashboard.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;

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
}