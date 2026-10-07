package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 发票 */
@Getter
@Setter
@Entity
@Table(name = "invoice")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String invoiceNo;

    @ManyToOne
    @JoinColumn(name = "charge_id", nullable = false)
    private ChargeRecord charge;

    /** 发票抬头 */
    @Column(nullable = false, length = 128)
    private String title;

    /** 税号 */
    @Column(length = 64)
    private String taxNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    /** 抬头错误更正后指向重开的发票 */
    @Column(length = 64)
    private String reissuedFrom;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
