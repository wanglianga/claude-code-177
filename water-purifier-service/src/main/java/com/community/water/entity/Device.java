package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 社区公共净水机设备 */
@Getter
@Setter
@Entity
@Table(name = "device")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设备编号（扫码识别） */
    @Column(nullable = false, unique = true, length = 64)
    private String deviceNo;

    /** 小区 */
    @Column(nullable = false, length = 128)
    private String community;

    /** 楼栋 */
    @Column(nullable = false, length = 64)
    private String building;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceStatus status = DeviceStatus.NORMAL;

    /** 暂停售水原因（居民端可见） */
    @Column(length = 512)
    private String pauseReason;

    private LocalDateTime installedAt;

    /** 最近一次维护（换芯/维修完成）时间 */
    private LocalDateTime lastMaintenanceAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
