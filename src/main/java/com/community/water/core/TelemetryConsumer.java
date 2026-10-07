package com.community.water.core;

import com.community.water.web.dto.Requests.TelemetryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.community.water.config.KafkaTopicConfig.TELEMETRY_TOPIC;

/**
 * 设备遥测 Kafka 消费者：净水机持续上报遥测到 telemetry.v1，
 * 在此落入同一套寿命/水质评估逻辑（与 REST 上报等价）。
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public TelemetryConsumer(TelemetryService telemetryService, ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TELEMETRY_TOPIC, groupId = "water-filter-telemetry",
            autoStartup = "${app.kafka.telemetry-listener-autostart:true}")
    public void onMessage(String message) {
        try {
            TelemetryRequest req = objectMapper.readValue(message, TelemetryRequest.class);
            telemetryService.ingest(req);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("遥测消息无法解析: {} | {}", message, e.getMessage());
            throw new IllegalArgumentException("遥测消息格式错误", e);
        } catch (Exception e) {
            log.error("遥测消息处理失败: {} | {}", message, e.getMessage());
            throw e;
        }
    }
}
