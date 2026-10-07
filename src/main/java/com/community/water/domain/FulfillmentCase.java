package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 设备履约链路（Case）：把一次异常从触发到闭环涉及的
 * 设备、滤芯、取水、投诉、报修、派单、换芯、复检、收费、发票全部串在一起。
 * 解决"设备继续出水但水质责任不清"的问题——每条记录都可追溯到同一 caseNo。
 */
@Entity
@Table(name = "fulfillment_cases", indexes = {
        @Index(name = "idx_case_device", columnList = "device_id,createdAt")
})
@Getter
@Setter
public class FulfillmentCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String caseNo;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private String deviceCode;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private String building;

    @Column(nullable = false, length = 32)
    private String primaryReason;

    @Column(nullable = false, length = 500)
    private String summary;

    /** OPEN / SUSPENDED / REPLACED / RETEST_PASSED / CLOSED */
    @Column(nullable = false, length = 24)
    private String stage = "OPEN";

    @Column(nullable = false)
    private boolean waterSuspended = false;

    /** 复检是否通过，只有通过才恢复售水并闭环 */
    @Column(nullable = false)
    private boolean retestPassed = false;

    /** 收费是否全部结清（取水费 + 楼栋分摊） */
    @Column(nullable = false)
    private boolean billingSettled = false;

    /** 发票抬头是否已校正无误 */
    @Column(nullable = false)
    private boolean invoiceCorrect = false;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime closedAt;

    @Column(length = 500)
    private String closeNote;
}
