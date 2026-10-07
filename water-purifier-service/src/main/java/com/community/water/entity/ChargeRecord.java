package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 收费记录（取水扣费 / 维护费分摊） */
@Getter
@Setter
@Entity
@Table(name = "charge_record", indexes = {
        @Index(name = "idx_charge_account", columnList = "account_id,created_at")
})
public class ChargeRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String chargeNo;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private ResidentAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChargeType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChargeStatus status;

    @Column(length = 256)
    private String failReason;

    /** 关联工单（维护费分摊场景） */
    @Column(length = 64)
    private String orderNo;

    /** 关联取水记录（取水扣费场景） */
    @Column(length = 64)
    private String intakeNo;

    /** 收费说明（居民端"为什么收费"） */
    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
