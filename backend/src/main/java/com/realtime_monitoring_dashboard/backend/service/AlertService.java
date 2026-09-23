package com.realtime_monitoring_dashboard.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtime_monitoring_dashboard.backend.dto.AlertDTO;
import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import com.realtime_monitoring_dashboard.backend.model.Device;
import com.realtime_monitoring_dashboard.backend.repository.AlertRepository;

import lombok.RequiredArgsConstructor;

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

    public AlertDTO acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        alert.setAcknowledged(true);
        Alert savedAlert = alertRepository.save(alert);
        AlertDTO dto = mapToDTO(savedAlert);

        messagingTemplate.convertAndSend("/topic/alerts", dto);

        return dto;
    }

    public AlertDTO resolveAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        alert.setResolved(true);
        Alert savedAlert = alertRepository.save(alert);
        AlertDTO dto = mapToDTO(savedAlert);

        messagingTemplate.convertAndSend("/topic/alerts", dto);
        return dto;
    }

    private AlertDTO mapToDTO(Alert alert) {
        return AlertDTO.builder()
                .id(alert.getId())
                .deviceId(alert.getDevice().getId())
                .deviceName(alert.getDevice().getName())
                .severity(alert.getSeverity())
                .message(alert.getMessage())
                .timestamp(alert.getTimestamp())
                .acknowledged(alert.isAcknowledged())
                .resolved(alert.isResolved())
                .build();
    }

    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
public void resolveActiveAlertsForDevice(Device device) {
    List<Alert> activeAlerts = alertRepository.findByDeviceIdAndResolvedFalse(device.getId());

    if (activeAlerts.isEmpty()) {
        return;
    }

    for (Alert alert : activeAlerts) {
        alert.setResolved(true);
        Alert savedAlert = alertRepository.save(alert);
        AlertDTO dto = mapToDTO(savedAlert);

        
        messagingTemplate.convertAndSend("/topic/alerts", dto);
    }
}

    
}