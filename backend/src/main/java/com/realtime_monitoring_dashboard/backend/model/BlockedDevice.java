package com.realtime_monitoring_dashboard.backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blocked_devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id_hash", nullable = false, unique = true, length = 64)
    private String machineIdHash;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private LocalDateTime blockedAt;
}
