package com.realtime_monitoring_dashboard.backend.repository;

import com.realtime_monitoring_dashboard.backend.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByResolvedFalseOrderByTimestampDesc();
}