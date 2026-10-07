package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 滤芯实例。寿命不是固定日期：取水量（usedLiters/expectedCapacityLiters）、
 * 水质、维护频率与投诉都会在策略服务中折算 lifePercent。
 */
@Entity
@Table(name = "water_filters")
@Getter
@Setter
public class WaterFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    /** 旧滤芯扫码确认的唯一编号 */
    @Column(nullable = false, unique = true)
    private String filterSerialNo;

    @Column(nullable = false)
    private String batchNo;

    @Column(nullable = false)
    private OffsetDateTime installedAt;

    /** 标称可处理水量（升） */
    @Column(nullable = false)
    private Double expectedCapacityLiters;

    @Column(nullable = false)
    private Double usedLiters = 0.0;

    /** 0-100，剩余寿命百分比；由遥测上报或策略重算 */
    @Column(nullable = false)
    private Integer lifePercent = 100;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FilterStatus status = FilterStatus.IN_USE;

    private OffsetDateTime removedAt;

    /** 提前耗尽标记：实际寿命明显短于标称（按出水量折算仍富余但寿命归零） */
    private boolean earlyExhausted = false;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
