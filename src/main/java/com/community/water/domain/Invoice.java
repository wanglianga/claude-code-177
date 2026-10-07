package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 发票：抬头错误时不作废消失，而是冲红（VOIDED）后重开（REISSUED），
 * 保留完整更正轨迹，保证收费可解释。
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNo;

    @Column(nullable = false)
    private Long chargeId;

    @Column(nullable = false)
    private String accountNo;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String taxNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    /** 被哪张新发票重开 */
    private Long reissuedById;

    /** 冲红的原发票 */
    private Long reissuedFromId;

    @Column(nullable = false)
    private OffsetDateTime issuedAt = OffsetDateTime.now();
}
