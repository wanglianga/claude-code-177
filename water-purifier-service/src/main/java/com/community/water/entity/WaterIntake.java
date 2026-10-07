package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 居民扫码取水记录 */
@Getter
@Setter
@Entity
@Table(name = "water_intake", indexes = {
        @Index(name = "idx_intake_device_time", columnList = "device_id,created_at"),
        @Index(name = "idx_intake_account", columnList = "account_id")
})
public class WaterIntake {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String intakeNo;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private ResidentAccount account;

    @Column(nullable = false, length = 128)
    private String community;

    @Column(nullable = false, length = 64)
    private String building;

    /** 取水量（升） */
    @Column(nullable = false)
    private double amountLiters;

    /** 单价（元/升） */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal unitPrice;

    /** 本次费用（元） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    /** 扣费后余额（元） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    /** 本次取水是否伴随异味投诉 */
    @Column(nullable = false)
    private boolean odorComplaint = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
