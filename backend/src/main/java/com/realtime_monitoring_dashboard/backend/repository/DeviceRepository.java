package com.realtime_monitoring_dashboard.backend.repository;

import com.realtime_monitoring_dashboard.backend.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
}