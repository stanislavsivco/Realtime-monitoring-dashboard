package com.realtime_monitoring_dashboard.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.realtime_monitoring_dashboard.backend.model.Metric;

@Repository
public interface MetricRepository extends JpaRepository<Metric, Long> {

    List<Metric> findTop100ByDeviceIdOrderByTimestampAsc(Long deviceId);

    Optional<Metric> findTopByDeviceIdOrderByTimestampDesc(Long deviceId);

}
