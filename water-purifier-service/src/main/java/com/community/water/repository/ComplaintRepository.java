package com.community.water.repository;

import com.community.water.entity.Complaint;
import com.community.water.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    long countByDeviceAndCreatedAtAfter(Device device, LocalDateTime after);

    List<Complaint> findByDeviceOrderByCreatedAtDesc(Device device);
}
