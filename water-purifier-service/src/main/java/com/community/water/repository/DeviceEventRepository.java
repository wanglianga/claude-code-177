package com.community.water.repository;

import com.community.water.entity.Device;
import com.community.water.entity.DeviceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long> {
    List<DeviceEvent> findByDeviceOrderByCreatedAtAsc(Device device);
}
