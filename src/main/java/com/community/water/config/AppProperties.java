package com.community.water.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.defaults")
public record AppProperties(
        int lifeThresholdPercent,
        int tdsLimit,
        double chlorineLimit,
        BigDecimal pricePerLiter,
        int arrivalSlaHours,
        int complaintWindowHours,
        int complaintClusterSize,
        int flushMinMinutes) {
}
