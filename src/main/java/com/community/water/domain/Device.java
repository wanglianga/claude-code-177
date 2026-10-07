package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 社区公共净水机。每台设备带可由运营动态调整的阈值（换芯策略不是固定日期）。 */
@Entity
@Table(name = "devices")
@Getter
@Setter
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String deviceCode;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private String communityName;

    /** 安装位置，如 3 号楼大堂 */
    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private String building;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @Column(name = "current_filter_id")
    private Long currentFilterId;

    // ---- 运营可按设备健康/投诉动态调整的策略阈值 ----
    @Column(nullable = false)
    private Integer lifeThresholdPercent = 20;

    @Column(nullable = false)
    private Integer tdsLimit = 100;

    @Column(nullable = false)
    private Double chlorineLimit = 2.0;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerLiter = new BigDecimal("0.30");

    /** 累计出水量（升），寿命动态计算因子之一 */
    @Column(nullable = false)
    private Double cumulativeOutputLiters = 0.0;

    /** 暂停/异常原因解释（居民端"为什么暂停"） */
    private String suspensionReason;

    private OffsetDateTime suspendedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt;
}
