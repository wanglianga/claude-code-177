package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 通知（物业/居民/师傅/运营） */
@Getter
@Setter
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notice_target", columnList = "target_type,target_ref,created_at")
})
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PROPERTY 物业 / RESIDENT 居民 / TECHNICIAN 师傅 / OPERATIONS 运营 */
    @Column(nullable = false, length = 32)
    private String targetType;

    /** 目标标识：小区+楼栋 / 账户号 / 师傅工号 */
    @Column(nullable = false, length = 128)
    private String targetRef;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 1024)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
