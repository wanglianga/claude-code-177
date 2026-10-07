package com.community.water.service;

import com.community.water.config.WaterRules;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.TechnicianRepository;
import com.community.water.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 工单调度：创建、派单、到场确认、超时改派 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final TechnicianRepository technicianRepository;
    private final DeviceEventService eventService;
    private final NotificationService notificationService;
    private final AlertService alertService;
    private final WaterRules rules;

    /**
     * 创建工单并自动派单。同设备同类型存在未完成工单时不重复创建。
     */
    @Transactional
    public WorkOrder createOrder(Device device, WorkOrderType type, String alertNo,
                                 boolean freeOfCharge, String remark) {
        boolean exists = workOrderRepository.existsByDeviceAndTypeAndStatusIn(device, type,
                List.of(WorkOrderStatus.PENDING, WorkOrderStatus.ASSIGNED, WorkOrderStatus.ARRIVED));
        if (exists) {
            log.info("work order dedup: device={} type={}", device.getDeviceNo(), type);
            return workOrderRepository.findByDeviceOrderByCreatedAtDesc(device).stream()
                    .filter(o -> o.getType() == type && List.of(WorkOrderStatus.PENDING,
                            WorkOrderStatus.ASSIGNED, WorkOrderStatus.ARRIVED).contains(o.getStatus()))
                    .findFirst().orElseThrow();
        }
        WorkOrder order = new WorkOrder();
        order.setOrderNo("WO" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        order.setDevice(device);
        order.setType(type);
        order.setAlertNo(alertNo);
        order.setFreeOfCharge(freeOfCharge);
        order.setRemark(remark);
        order.setScheduledAt(LocalDateTime.now());
        order.setDeadlineAt(LocalDateTime.now().plusHours(rules.workorder().arriveDeadlineHours()));
        assign(order, null);
        workOrderRepository.save(order);
        eventService.record(device, "ORDER_CREATED", order.getOrderNo(),
                "工单创建[" + type + "] " + remark + "，师傅 " + order.getTechnician().getName()
                        + "，要求 " + rules.workorder().arriveDeadlineHours() + " 小时内到场");
        return order;
    }

    /** 派单给可用师傅（excludeTechNo 用于改派时排除原师傅） */
    private void assign(WorkOrder order, String excludeTechNo) {
        List<Technician> available = technicianRepository.findByStatus("AVAILABLE").stream()
                .filter(t -> excludeTechNo == null || !t.getTechNo().equals(excludeTechNo))
                .toList();
        if (available.isEmpty()) {
            throw BusinessException.conflict("当前无可用师傅，无法派单");
        }
        Technician tech = available.get(0);
        order.setTechnician(tech);
        order.setStatus(WorkOrderStatus.ASSIGNED);
        notificationService.notify(NotificationService.TECHNICIAN, tech.getTechNo(), "ORDER_ASSIGNED",
                "新工单 " + order.getOrderNo() + "（" + order.getType() + "），设备 "
                        + order.getDevice().getDeviceNo() + "，请于 " + order.getDeadlineAt() + " 前到场");
    }

    /** 师傅到场确认 */
    @Transactional
    public WorkOrder arrive(String orderNo, String techNo) {
        WorkOrder order = getOrder(orderNo);
        if (order.getStatus() != WorkOrderStatus.ASSIGNED) {
            throw BusinessException.conflict("工单状态为 " + order.getStatus() + "，不能确认到场");
        }
        if (!order.getTechnician().getTechNo().equals(techNo)) {
            throw new BusinessException(403, "该工单未派给师傅 " + techNo);
        }
        order.setStatus(WorkOrderStatus.ARRIVED);
        order.setArrivedAt(LocalDateTime.now());
        workOrderRepository.save(order);
        eventService.record(order.getDevice(), "ORDER_ARRIVED", orderNo,
                "师傅 " + order.getTechnician().getName() + " 到场");
        return order;
    }

    /**
     * 超时巡检：超过到场时限仍未到场的工单 → 未到场预警 + 改派其他师傅。
     *
     * @return 被改派的工单数
     */
    @Transactional
    public int escalateOverdueOrders() {
        List<WorkOrder> overdue = workOrderRepository.findByStatusAndDeadlineAtBefore(
                WorkOrderStatus.ASSIGNED, LocalDateTime.now());
        int count = 0;
        for (WorkOrder order : overdue) {
            Device device = order.getDevice();
            String oldTech = order.getTechnician().getTechNo();
            alertService.raise(device, AlertType.TECHNICIAN_NO_SHOW, "WARN",
                    "工单 " + order.getOrderNo() + " 师傅 " + order.getTechnician().getName()
                            + " 未在 " + rules.workorder().arriveDeadlineHours() + " 小时内到场，已改派");
            try {
                assign(order, oldTech);
                order.setDeadlineAt(LocalDateTime.now().plusHours(rules.workorder().arriveDeadlineHours()));
                workOrderRepository.save(order);
                eventService.record(device, "ORDER_ESCALATED", order.getOrderNo(),
                        "师傅未按时到场，工单改派给 " + order.getTechnician().getName());
                count++;
            } catch (BusinessException e) {
                log.warn("reassign failed for {}: {}", order.getOrderNo(), e.getMessage());
            }
        }
        return count;
    }

    @Transactional(readOnly = true)
    public WorkOrder getOrder(String orderNo) {
        return workOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> BusinessException.notFound("工单 " + orderNo));
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> listByDevice(Device device) {
        return workOrderRepository.findByDeviceOrderByCreatedAtDesc(device);
    }
}
