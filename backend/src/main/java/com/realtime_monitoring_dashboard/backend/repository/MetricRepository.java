package com.realtime_monitoring_dashboard.backend.repository;

import com.realtime_monitoring_dashboard.backend.model.Metric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<Metric, Long> {


    List<Metric> findTop100ByDeviceIdOrderByTimestampAsc(Long deviceId);

}
