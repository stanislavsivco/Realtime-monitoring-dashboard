package com.realtime_monitoring_dashboard.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.realtime_monitoring_dashboard.backend.model.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByResolvedFalseOrderByTimestampDesc();

    List<Alert> findByDeviceIdAndResolvedFalse(Long deviceId);
}