package com.community.water.repository;

import com.community.water.entity.Device;
import com.community.water.entity.FilterReplacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FilterReplacementRepository extends JpaRepository<FilterReplacement, Long> {
    List<FilterReplacement> findByDeviceOrderByCompletedAtDesc(Device device);

    Optional<FilterReplacement> findTopByDeviceOrderByCompletedAtDesc(Device device);
}
