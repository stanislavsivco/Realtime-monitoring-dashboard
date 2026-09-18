package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.dto.AlertDTO;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    public void createAlert(Device device, AlertSeverity severity, String message) {
        Alert alert = Alert.builder()
                .device(device)
                .severity(severity)
                .message(message)
                .timestamp(LocalDateTime.now())
                .resolved(false)
                .build();
        alertRepository.save(alert);
    }

    public List<AlertDTO> getActiveAlerts() {
        return alertRepository.findByResolvedFalseOrderByTimestampDesc()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    public AlertDTO resolveAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        alert.setResolved(true);
        return mapToDTO(alertRepository.save(alert));
    }

    private AlertDTO mapToDTO(Alert alert) {
        return AlertDTO.builder()
                .id(alert.getId())
                .deviceId(alert.getDevice().getId())
                .deviceName(alert.getDevice().getName())
                .severity(alert.getSeverity())
                .message(alert.getMessage())
                .timestamp(alert.getTimestamp())
                .resolved(alert.isResolved())
                .build();
    }
}