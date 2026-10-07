package com.community.water.repository;

import com.community.water.entity.Device;
import com.community.water.entity.FilterCartridge;
import com.community.water.entity.FilterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FilterCartridgeRepository extends JpaRepository<FilterCartridge, Long> {
    Optional<FilterCartridge> findByFilterNo(String filterNo);

    Optional<FilterCartridge> findByDeviceAndStatus(Device device, FilterStatus status);

    List<FilterCartridge> findByDeviceOrderByInstalledAtDesc(Device device);
}
