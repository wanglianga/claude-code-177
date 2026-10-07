package com.community.water.core;

import com.community.water.domain.OutboxEvent;
import com.community.water.repo.Repositories.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** 轮询 outbox 投递 Kafka。失败保留待重试，不阻塞主业务。 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findFirst100ByPublishedFalseOrderByIdAsc();
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
                event.setPublished(true);
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                outboxRepository.save(event);
                log.warn("事件投递失败 topic={} type={} attempts={}: {}",
                        event.getTopic(), event.getEventType(), event.getAttempts(), e.getMessage());
            }
        }
    }
}
