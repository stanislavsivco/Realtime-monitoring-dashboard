package com.realtime_monitoring_dashboard.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.realtime_monitoring_dashboard.backend.model.Threshold;

@Repository
public interface ThresholdRepository extends JpaRepository<Threshold, Long> {
    Optional<Threshold> findByDeviceId(Long deviceId);

    @Modifying
    @Query("DELETE FROM Threshold t WHERE t.device.id = :deviceId")
    int deleteByDeviceId(@Param("deviceId") Long deviceId);
}
