package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 事件发件箱（Transactional Outbox）：业务数据与 Kafka 事件在同一事务落库，
 * 由发布器轮询投递，避免"库已提交但事件丢失"或反之。
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(nullable = false, length = 128)
    private String eventKey;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime publishedAt;
}
