package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 预警：寿命、水质、投诉、迟到、扣费失败、监管抽查等统一在此呈现与关闭。 */
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_device", columnList = "device_id,status")
})
@Getter
@Setter
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private Long communityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AlertType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status = AlertStatus.OPEN;

    private Long ticketId;

    private Long caseId;

    /** 扣费失败告警对应收费记录，重试成功后精准关闭 */
    private Long chargeId;

    /** 关联投诉编号，便于溯源 */
    private Long complaintId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime handledAt;
}
