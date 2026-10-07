package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 水质复检记录：换芯后的独立复检，也承载监管抽查。
 * 失败会重新打开工单/再次暂停售水。
 */
@Entity
@Table(name = "water_quality_retests")
@Getter
@Setter
public class WaterQualityRetest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    private Long ticketId;

    private Long replacementId;

    /** POST_REPLACEMENT 换芯复检；REGULATORY 监管抽查 */
    @Column(nullable = false, length = 32)
    private String retestType;

    @Column(nullable = false)
    private Double tds;

    @Column(nullable = false)
    private Double chlorine;

    @Column(nullable = false)
    private Double flowRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RetestResult result;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private String inspector;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
