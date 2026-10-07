package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 收费记录：居民取水费（可能扣费失败）与楼栋分摊维护费。
 * 失败可重试；与发票一一对应。
 */
@Entity
@Table(name = "charges")
@Getter
@Setter
public class Charge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String chargeNo;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private String building;

    @Column(nullable = false)
    private String accountNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChargeType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChargeStatus status = ChargeStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 300)
    private String failReason;

    private Long dispenseId;

    private Long ticketId;

    private Long caseId;

    private Long invoiceId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime paidAt;
}
