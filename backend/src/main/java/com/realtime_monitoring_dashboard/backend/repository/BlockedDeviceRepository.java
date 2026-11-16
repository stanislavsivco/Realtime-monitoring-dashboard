package com.realtime_monitoring_dashboard.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.realtime_monitoring_dashboard.backend.model.BlockedDevice;

@Repository
public interface BlockedDeviceRepository extends JpaRepository<BlockedDevice, Long> {

    boolean existsByMachineIdHash(String machineIdHash);

    List<BlockedDevice> findAllByOrderByBlockedAtDesc();
}
