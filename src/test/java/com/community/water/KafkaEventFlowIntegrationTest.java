package com.community.water;

import com.community.water.domain.Device;
import com.community.water.domain.DeviceStatus;
import com.community.water.domain.OutboxEvent;
import com.community.water.repo.Repositories.DeviceRepository;
import com.community.water.repo.Repositories.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka 事件链路测试（EmbeddedKafka）：
 * 设备遥测消息进入 telemetry.v1 → TelemetryConsumer 入库并触发水质评估
 * → 暂停售水 → outbox 事件可靠投递到 device-events.v1。
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"telemetry.v1", "dispense.v1", "device-events.v1"})
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "app.kafka.telemetry-listener-autostart=true",
        "app.outbox.fixed-delay-ms=500",
        "app.scheduler.late-scan-ms=86400000",
        "app.scheduler.reconcile-ms=86400000"
})
class KafkaEventFlowIntegrationTest {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private OutboxRepository outboxRepository;

    @Test
    void telemetryMessage_drivesSuspension_andEventsReachKafka() throws Exception {
        Map<String, Object> payload = Map.of(
                "deviceCode", "WQ-1002",
                "eventTime", OffsetDateTime.now().toString(),
                "filterLifePercent", 85,
                "waterOutputLiters", 5.0,
                "tds", 220.0,
                "chlorine", 0.6,
                "flowRate", 2.0,
                "faultCode", "E77",
                "lastMaintenanceAt", OffsetDateTime.now().minusDays(10).toString());
        kafkaTemplate.send("telemetry.v1", "WQ-1002", objectMapper.writeValueAsString(payload)).get();

        // 消费者异步处理：设备应被暂停
        await().atMost(20, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Device d = deviceRepository.findByDeviceCode("WQ-1002").orElseThrow();
            assertEquals(DeviceStatus.SUSPENDED, d.getStatus());
            assertNotNull(d.getSuspensionReason());
            assertTrue(d.getSuspensionReason().contains("E77"));
        });

        // 发件箱事件应已投递到 Kafka（published=true）
        await().atMost(15, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            List<OutboxEvent> events = outboxRepository.findAll().stream()
                    .filter(e -> "WQ-1002".equals(e.getEventKey())).toList();
            assertFalse(events.isEmpty());
            assertTrue(events.stream().anyMatch(OutboxEvent::isPublished));
            assertTrue(events.stream().anyMatch(e -> "WATER_QUALITY_ABNORMAL".equals(e.getEventType())));
            assertTrue(events.stream().anyMatch(e -> "DEVICE_SUSPENDED".equals(e.getEventType())));
        });
    }
}
