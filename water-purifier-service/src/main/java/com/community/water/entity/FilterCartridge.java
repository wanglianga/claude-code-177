package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 滤芯：寿命按动态消耗计算，并非固定日期 */
@Getter
@Setter
@Entity
@Table(name = "filter_cartridge")
public class FilterCartridge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 滤芯编号（旧滤芯扫码确认用） */
    @Column(nullable = false, unique = true, length = 64)
    private String filterNo;

    /** 生产批次号 */
    @Column(nullable = false, length = 64)
    private String batchNo;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** 额定净水量（升） */
    @Column(nullable = false)
    private double ratedCapacityLiters;

    /** 已消耗净水量（升，按水质加权后的有效消耗） */
    @Column(nullable = false)
    private double usedLiters = 0;

    /** 剩余寿命百分比 0-100（动态计算） */
    @Column(nullable = false)
    private double lifePercent = 100;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FilterStatus status = FilterStatus.ACTIVE;

    private LocalDateTime installedAt = LocalDateTime.now();

    private LocalDateTime replacedAt;
}
