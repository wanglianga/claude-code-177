package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 对外通知：暂停售水通知物业/居民、派单通知师傅、复检结果公示等。 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private Long communityId;

    /** RESIDENT / PROPERTY / TECHNICIAN / OPERATOR */
    @Column(nullable = false, length = 16)
    private String targetRole;

    private String targetRef;

    @Column(nullable = false, length = 64)
    private String channel; // SMS / APP / DISPLAY

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 600)
    private String content;

    private Long caseId;

    private Long ticketId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private boolean delivered = false;
}
