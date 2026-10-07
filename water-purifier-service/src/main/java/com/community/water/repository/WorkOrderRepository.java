package com.community.water.repository;

import com.community.water.entity.Device;
import com.community.water.entity.WorkOrder;
import com.community.water.entity.WorkOrderStatus;
import com.community.water.entity.WorkOrderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    Optional<WorkOrder> findByOrderNo(String orderNo);

    boolean existsByDeviceAndTypeAndStatusIn(Device device, WorkOrderType type, List<WorkOrderStatus> statuses);

    List<WorkOrder> findByStatusAndDeadlineAtBefore(WorkOrderStatus status, LocalDateTime deadline);

    List<WorkOrder> findByDeviceOrderByCreatedAtDesc(Device device);
}
