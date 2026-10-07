package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 维护工单（报修/换芯/复检） */
@Getter
@Setter
@Entity
@Table(name = "work_order", indexes = {
        @Index(name = "idx_order_device", columnList = "device_id,status")
})
public class WorkOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderNo;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** 触发工单来源预警 */
    @Column(length = 64)
    private String alertNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkOrderType type;

    @ManyToOne
    @JoinColumn(name = "technician_id")
    private Technician technician;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkOrderStatus status = WorkOrderStatus.PENDING;

    /** 是否免费（换芯后仍有异味等返工场景免费） */
    @Column(nullable = false)
    private boolean freeOfCharge = false;

    @Column(length = 512)
    private String remark;

    private LocalDateTime scheduledAt;

    /** 到场时限 */
    private LocalDateTime deadlineAt;

    private LocalDateTime arrivedAt;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
