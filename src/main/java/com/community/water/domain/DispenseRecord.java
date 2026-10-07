package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 居民扫码取水记录：小区、楼栋、取水量、账户余额与是否出现异味投诉。 */
@Entity
@Table(name = "dispense_records", indexes = {
        @Index(name = "idx_disp_device_time", columnList = "device_id,dispensedAt")
})
@Getter
@Setter
public class DispenseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private String deviceCode;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private String building;

    @Column(nullable = false)
    private String accountNo;

    @Column(nullable = false)
    private OffsetDateTime dispensedAt;

    @Column(nullable = false)
    private Double liters;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 扣费后账户余额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private boolean chargeSucceeded;

    /** 当次取水是否被居民标记异味 */
    @Column(nullable = false)
    private boolean smellComplaint = false;

    private Long chargeId;

    private Long caseId;
}
