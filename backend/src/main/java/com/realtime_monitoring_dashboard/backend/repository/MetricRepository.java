package com.realtime_monitoring_dashboard.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO;
import com.realtime_monitoring_dashboard.backend.model.Metric;

@Repository
public interface MetricRepository extends JpaRepository<Metric, Long> {

    List<Metric> findTop120ByDeviceIdOrderByTimestampAsc(Long deviceId);

    Optional<Metric> findTopByDeviceIdOrderByTimestampDesc(Long deviceId);

    Optional<Metric> findFirstByDeviceIdOrderByTimestampDesc(Long deviceId);

    Page<Metric> findByDeviceId(Long deviceId, Pageable pageable);

    Page<Metric> findByDeviceIdAndTimestampBetween(
        Long deviceId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );

    @Query("SELECT new com.realtime_monitoring_dashboard.backend.dto.MetricSummaryDTO(" +
       "AVG(m.cpu), MAX(m.cpu), MIN(m.cpu), " +
       "AVG(m.ram), MAX(m.ram), MIN(m.ram), " +
       "AVG(m.disk), MAX(m.disk), MIN(m.disk), " +
       "AVG(m.latencyMs)) " +
       "FROM Metric m WHERE m.device.id = :deviceId AND m.timestamp BETWEEN :startDate AND :endDate")
    MetricSummaryDTO getMetricSummary(
        @Param("deviceId") Long deviceId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
);

}
