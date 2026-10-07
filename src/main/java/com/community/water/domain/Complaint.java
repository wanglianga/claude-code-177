package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 异味/水质投诉，聚集时触发换芯并挂入设备履约链路。 */
@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_comp_device_time", columnList = "device_id,createdAt")
})
@Getter
@Setter
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private String building;

    @Column(nullable = false)
    private String accountNo;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    /** 关联的取水记录（可空，物业也可代登记） */
    private Long dispenseId;

    private Long ticketId;

    private Long caseId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime resolvedAt;
}
