package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 预警 */
@Getter
@Setter
@Entity
@Table(name = "alert", indexes = {
        @Index(name = "idx_alert_device", columnList = "device_id,status")
})
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String alertNo;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private AlertType type;

    /** INFO / WARN / CRITICAL */
    @Column(nullable = false, length = 16)
    private String level;

    @Column(nullable = false, length = 1024)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;
}
