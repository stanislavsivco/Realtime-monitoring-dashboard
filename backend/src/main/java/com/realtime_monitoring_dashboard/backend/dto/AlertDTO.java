package com.realtime_monitoring_dashboard.backend.dto;

import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private AlertSeverity severity;
    private String message;
    private LocalDateTime timestamp;
    private boolean acknowledged;
    private boolean resolved;
}