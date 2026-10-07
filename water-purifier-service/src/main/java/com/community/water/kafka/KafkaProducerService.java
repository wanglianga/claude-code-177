package com.community.water.kafka;

import com.community.water.config.WaterRules;
import com.community.water.kafka.msg.AlertMessage;
import com.community.water.kafka.msg.IntakeMessage;
import com.community.water.kafka.msg.NotificationMessage;
import com.community.water.kafka.msg.TelemetryMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** 统一 Kafka 生产入口 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WaterRules rules;

    public void sendTelemetry(TelemetryMessage msg) {
        kafkaTemplate.send(rules.topics().telemetry(), msg.deviceNo(), msg);
        log.debug("telemetry -> kafka: {}", msg.deviceNo());
    }

    public void sendIntake(IntakeMessage msg) {
        kafkaTemplate.send(rules.topics().intake(), msg.deviceNo(), msg);
    }

    public void sendAlert(AlertMessage msg) {
        kafkaTemplate.send(rules.topics().alert(), msg.deviceNo(), msg);
    }

    public void sendNotification(NotificationMessage msg) {
        kafkaTemplate.send(rules.topics().notification(), msg.targetRef(), msg);
    }
}
