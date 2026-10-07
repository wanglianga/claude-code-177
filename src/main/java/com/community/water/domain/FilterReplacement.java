package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 换芯记录：师傅扫码确认旧滤芯编号、新滤芯批次、安装照片、
 * 冲洗时间（分钟）和复检结果。每一次换芯必须真实留痕。
 */
@Entity
@Table(name = "filter_replacements")
@Getter
@Setter
public class FilterReplacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private String deviceCode;

    @Column(nullable = false)
    private Long technicianId;

    @Column(nullable = false)
    private String technicianName;

    /** 拆下的旧滤芯编号（扫码确认，须与设备当前滤芯一致） */
    @Column(nullable = false)
    private String oldFilterSerialNo;

    /** 新滤芯批次（扫码） */
    @Column(nullable = false)
    private String newFilterSerialNo;

    @Column(nullable = false)
    private String newFilterBatchNo;

    /** 安装照片 URL 或 OSS key，证明换芯真实发生 */
    @Column(nullable = false, length = 500)
    private String installPhotoUrl;

    /** 冲洗时间（分钟），不足则不允许复检 */
    @Column(nullable = false)
    private Integer flushMinutes;

    @Column(nullable = false)
    private OffsetDateTime replacedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RetestResult retestResult = RetestResult.PENDING;

    private OffsetDateTime retestedAt;

    /** 复检 TDS / 余氯 */
    private Double retestTds;

    private Double retestChlorine;

    @Column(length = 500)
    private String retestNote;

    private Long newFilterId;

    private Long caseId;
}
