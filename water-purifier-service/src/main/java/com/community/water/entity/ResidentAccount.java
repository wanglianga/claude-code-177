package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 居民账户 */
@Getter
@Setter
@Entity
@Table(name = "resident_account")
public class ResidentAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String accountNo;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 128)
    private String community;

    @Column(nullable = false, length = 64)
    private String building;

    @Column(length = 32)
    private String roomNo;

    @Column(length = 32)
    private String phone;

    /** 账户余额（元） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    private LocalDateTime createdAt = LocalDateTime.now();
}
