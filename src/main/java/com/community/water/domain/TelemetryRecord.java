package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 设备遥测：设备编号持续上报滤芯寿命、出水量、TDS、余氯、流量、故障码、最近维护时间。 */
@Entity
@Table(name = "telemetry_records", indexes = {
        @Index(name = "idx_tel_device_time", columnList = "device_id,eventTime")
})
@Getter
@Setter
public class TelemetryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private String deviceCode;

    @Column(nullable = false)
    private OffsetDateTime eventTime;

    /** 滤芯剩余寿命 % */
    @Column(nullable = false)
    private Integer filterLifePercent;

    /** 本次上报周期内出水量（升） */
    @Column(nullable = false)
    private Double waterOutputLiters;

    /** 水质 TDS mg/L */
    @Column(nullable = false)
    private Double tds;

    /** 余氯 mg/L */
    @Column(nullable = false)
    private Double chlorine;

    /** 瞬时流量 L/min */
    @Column(nullable = false)
    private Double flowRate;

    /** 故障码，0/空表示无故障 */
    @Column(length = 32)
    private String faultCode;

    private OffsetDateTime lastMaintenanceAt;
}
