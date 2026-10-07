package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 居民投诉（异味/水质等） */
@Getter
@Setter
@Entity
@Table(name = "complaint", indexes = {
        @Index(name = "idx_complaint_device_time", columnList = "device_id,created_at")
})
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private ResidentAccount account;

    /** 关联的取水记录（扫码取水口诉时） */
    @Column(length = 64)
    private String intakeNo;

    /** ODOR 异味 / QUALITY 水质 / OTHER 其他 */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 512)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
