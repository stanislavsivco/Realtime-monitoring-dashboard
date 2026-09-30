package com.realtime_monitoring_dashboard.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.realtime_monitoring_dashboard.backend.model.Device;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByName(String name);
}