package com.realtime_monitoring_dashboard.backend.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


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

}
