package com.community.water.kafka.msg;

import java.time.LocalDateTime;

/** 设备遥测上报消息 */
public record TelemetryMessage(
        String deviceNo,
        Double filterLifePercent,
        Double totalOutputLiters,
        Double tds,
        Double residualChlorine,
        Double flowRate,
        String faultCode,
        LocalDateTime lastMaintenanceAt
) {}
