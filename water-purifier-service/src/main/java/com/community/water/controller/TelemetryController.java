package com.community.water.controller;

import com.community.water.dto.ApiDtos.TelemetryReportRequest;
import com.community.water.kafka.KafkaProducerService;
import com.community.water.kafka.msg.TelemetryMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "设备遥测", description = "设备持续上报遥测，经 Kafka 异步处理")
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final KafkaProducerService producer;

    @Operation(summary = "设备上报遥测（滤芯寿命/出水量/TDS/余氯/流量/故障码/最近维护时间）")
    @PostMapping
    public Map<String, String> report(@Valid @RequestBody TelemetryReportRequest req) {
        producer.sendTelemetry(new TelemetryMessage(
                req.deviceNo(), req.filterLifePercent(), req.totalOutputLiters(),
                req.tds(), req.residualChlorine(), req.flowRate(),
                req.faultCode(), req.lastMaintenanceAt()));
        return Map.of("status", "accepted", "deviceNo", req.deviceNo());
    }
}
