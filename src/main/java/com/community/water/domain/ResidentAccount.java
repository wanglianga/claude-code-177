package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 居民储水账户：扫码取水时按升扣费，记录余额。 */
@Entity
@Table(name = "resident_accounts")
@Getter
@Setter
public class ResidentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNo;

    @Column(nullable = false)
    private String residentName;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private String building;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
