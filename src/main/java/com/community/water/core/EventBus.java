package com.community.water.core;

import com.community.water.domain.OutboxEvent;
import com.community.water.repo.Repositories.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件总线：在业务事务内把事件写入 outbox（事务发件箱），
 * 与业务数据同提交；{@link OutboxPublisher} 再异步投递 Kafka。
 * 无 Kafka 的测试环境下事件仍可靠落库、可断言。
 */
@Component
public class EventBus {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EventBus(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void emit(String topic, String eventType, String eventKey, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setEventType(eventType);
        event.setEventKey(eventKey);
        event.setPayload(toJson(payload));
        outboxRepository.save(event);
    }

    public static Map<String, Object> payload(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("事件序列化失败", e);
        }
    }
}
