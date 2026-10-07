package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 换芯过程记录：师傅扫码确认旧滤芯、新滤芯批次、安装照片、冲洗时间、复检结果 */
@Getter
@Setter
@Entity
@Table(name = "filter_replacement")
public class FilterReplacement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "work_order_id", nullable = false, unique = true)
    private WorkOrder workOrder;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** 旧滤芯编号（扫码确认） */
    @Column(nullable = false, length = 64)
    private String oldFilterNo;

    /** 新滤芯编号 */
    @Column(nullable = false, length = 64)
    private String newFilterNo;

    /** 新滤芯批次 */
    @Column(nullable = false, length = 64)
    private String newBatchNo;

    /** 安装照片 URL，逗号分隔 */
    @Column(nullable = false, length = 1024)
    private String photoUrls;

    /** 冲洗时间（分钟） */
    @Column(nullable = false)
    private int flushMinutes;

    /** 复检 TDS */
    private Double recheckTds;

    /** 复检余氯 */
    private Double recheckChlorine;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private RecheckResult recheckResult;

    @Column(nullable = false)
    private LocalDateTime completedAt = LocalDateTime.now();
}
