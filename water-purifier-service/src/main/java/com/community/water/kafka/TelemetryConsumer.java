package com.community.water.kafka;

import com.community.water.kafka.msg.TelemetryMessage;
import com.community.water.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 设备遥测消费者 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryConsumer {

    private final TelemetryService telemetryService;

    @KafkaListener(topics = "${water.topics.telemetry}", groupId = "water-purifier-service")
    public void onTelemetry(TelemetryMessage msg) {
        log.info("telemetry <- kafka: device={} tds={} output={}",
                msg.deviceNo(), msg.tds(), msg.totalOutputLiters());
        telemetryService.process(msg);
    }
}
