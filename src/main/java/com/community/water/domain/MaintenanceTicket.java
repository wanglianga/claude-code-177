package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 报修/维护工单：预警派单、师傅到场、迟到升级都在这张表上推进。 */
@Entity
@Table(name = "maintenance_tickets")
@Getter
@Setter
public class MaintenanceTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticketNo;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private String deviceCode;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private String building;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketTrigger trigger;

    @Column(length = 500)
    private String triggerDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    private Long technicianId;

    private String technicianName;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /** 派单时间 */
    private OffsetDateTime assignedAt;

    /** 要求到场时限（assignedAt + SLA） */
    private OffsetDateTime dueAt;

    private OffsetDateTime arrivedAt;

    /** 迟到告警是否已发出 */
    @Column(nullable = false)
    private boolean lateAlerted = false;

    private Long filterReplacementId;

    private Long caseId;

    private Long closedAlertId;

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
