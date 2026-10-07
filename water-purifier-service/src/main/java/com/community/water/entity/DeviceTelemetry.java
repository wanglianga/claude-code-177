package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 设备持续上报的遥测数据 */
@Getter
@Setter
@Entity
@Table(name = "device_telemetry", indexes = {
        @Index(name = "idx_telemetry_device_time", columnList = "device_id,reported_at")
})
public class DeviceTelemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** 设备自报滤芯寿命（%） */
    private Double filterLifePercent;

    /** 累计出水量（升） */
    private Double totalOutputLiters;

    /** 水质 TDS（mg/L） */
    private Double tds;

    /** 余氯（mg/L） */
    private Double residualChlorine;

    /** 瞬时流量（L/min） */
    private Double flowRate;

    /** 故障码，无故障为空 */
    @Column(length = 32)
    private String faultCode;

    /** 设备侧最近维护时间 */
    private LocalDateTime lastMaintenanceAt;

    @Column(nullable = false)
    private LocalDateTime reportedAt = LocalDateTime.now();
}
