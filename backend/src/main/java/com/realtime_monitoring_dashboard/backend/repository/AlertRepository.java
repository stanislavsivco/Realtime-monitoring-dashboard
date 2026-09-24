package com.realtime_monitoring_dashboard.backend.repository;

import java.util.List;

import com.realtime_monitoring_dashboard.backend.model.Alert;
import com.realtime_monitoring_dashboard.backend.model.AlertSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByResolvedFalseOrderByTimestampDesc();

    List<Alert> findByDeviceIdAndResolvedFalse(Long deviceId);

    @Query ("SELECT a FROM Alert a WHERE " + 
    "(:resolved IS NULL OR a.resolved = :resolved) AND " +
    "(severity IS NULL OR a.severity = :severity)")
    Page<Alert> findAlertsFiltered(
    @Param ("resolved") Boolean resolved,
    @Param ("severity") AlertSeverity severity,
    Pageable pageable
    );
}