package com.community.water.repository;

import com.community.water.entity.Device;
import com.community.water.entity.RecheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RecheckRecordRepository extends JpaRepository<RecheckRecord, Long> {
    List<RecheckRecord> findByDeviceAndCreatedAtBetweenOrderByCreatedAt(
            Device device, LocalDateTime from, LocalDateTime to);

    List<RecheckRecord> findByDeviceOrderByCreatedAtDesc(Device device);
}
