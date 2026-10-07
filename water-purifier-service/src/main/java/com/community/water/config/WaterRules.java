package com.community.water.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 业务规则配置：滤芯寿命并非固定日期，取水量、水质、维护频率和投诉都会改变换芯策略。
 */
@ConfigurationProperties(prefix = "water")
public record WaterRules(
        Pricing pricing,
        Filter filter,
        Quality quality,
        Complaint complaint,
        Workorder workorder,
        Topics topics
) {
    public record Pricing(BigDecimal unitPricePerLiter) {}

    public record Filter(int warnPercent, int exhaustedPercent, int minFlushMinutes) {}

    public record Quality(int tdsMax, double chlorineMin, double chlorineMax, int tdsStressBase) {}

    public record Complaint(int clusterThreshold, int clusterWindowHours, int postReplacementOdorDays) {}

    public record Workorder(int arriveDeadlineHours, int checkIntervalSeconds) {}

    public record Topics(String telemetry, String intake, String alert, String notification) {}
}
