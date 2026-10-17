package com.realtime_monitoring_dashboard.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.realtime_monitoring_dashboard.backend.model.Device;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Device> findBySimulatedTrue();
}
