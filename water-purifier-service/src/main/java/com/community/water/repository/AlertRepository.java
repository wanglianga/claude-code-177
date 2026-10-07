package com.community.water.repository;

import com.community.water.entity.Alert;
import com.community.water.entity.AlertStatus;
import com.community.water.entity.AlertType;
import com.community.water.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    boolean existsByDeviceAndTypeAndStatusIn(Device device, AlertType type, List<AlertStatus> statuses);

    List<Alert> findByDeviceOrderByCreatedAtDesc(Device device);

    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);
}
