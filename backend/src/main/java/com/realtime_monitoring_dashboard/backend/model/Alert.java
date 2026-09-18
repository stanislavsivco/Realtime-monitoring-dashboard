package com.realtime_monitoring_dashboard.backend.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity 
@Table(name = "alerts")
@Data 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 

public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private boolean resolved;
}
