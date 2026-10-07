package com.community.water.repository;

import com.community.water.entity.Device;
import com.community.water.entity.DeviceTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, Long> {
    Optional<DeviceTelemetry> findTopByDeviceOrderByReportedAtDesc(Device device);

    List<DeviceTelemetry> findByDeviceAndReportedAtBetweenOrderByReportedAt(
            Device device, LocalDateTime from, LocalDateTime to);
}
