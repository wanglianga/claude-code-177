package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 设备履约链路事件：设备、滤芯、取水、报修、换芯、收费、发票、复检统一时间线。
 */
@Getter
@Setter
@Entity
@Table(name = "device_event", indexes = {
        @Index(name = "idx_event_device_time", columnList = "device_id,created_at")
})
public class DeviceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /**
     * TELEMETRY / FILTER_LIFE / INTAKE / COMPLAINT / ALERT / ORDER_CREATED /
     * ORDER_ASSIGNED / ORDER_ARRIVED / ORDER_ESCALATED / REPLACEMENT /
     * CHARGE / CHARGE_FAILED / INVOICE / INVOICE_REISSUED / RECHECK /
     * DEVICE_PAUSED / DEVICE_RESUMED / NOTIFICATION
     */
    @Column(nullable = false, length = 48)
    private String eventType;

    /** 关联业务单号 */
    @Column(length = 64)
    private String refNo;

    @Column(nullable = false, length = 1024)
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
