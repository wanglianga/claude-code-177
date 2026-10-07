package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 复检记录（换芯复检 / 监管抽查水质） */
@Getter
@Setter
@Entity
@Table(name = "recheck_record", indexes = {
        @Index(name = "idx_recheck_device_time", columnList = "device_id,created_at")
})
public class RecheckRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** 关联换芯记录（换芯复检场景） */
    @Column
    private Long replacementId;

    /** 关联工单 */
    @Column(length = 64)
    private String orderNo;

    /** REPLACEMENT 换芯复检 / REGULATORY 监管抽查 / MANUAL 人工复检 */
    @Column(nullable = false, length = 32)
    private String kind;

    private Double tds;

    private Double residualChlorine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecheckResult result;

    /** 检查人（师傅/监管员） */
    @Column(length = 64)
    private String inspector;

    @Column(length = 512)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
