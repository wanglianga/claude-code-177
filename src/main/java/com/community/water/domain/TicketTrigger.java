package com.community.water.domain;

/** 报修/工单触发原因，决定履约链路性质。 */
public enum TicketTrigger {
    LIFE_WARNING,            // 滤芯寿命接近阈值的预警
    LIFE_EXHAUSTED_EARLY,    // 滤芯寿命提前耗尽
    WATER_QUALITY,           // 水质异常（TDS/余氯/故障码）
    COMPLAINT_CLUSTER,       // 异味投诉集中
    SMELL_AFTER_REPLACE,     // 换芯后仍有异味
    REGULATORY_AUDIT,        // 监管抽查
    MANUAL                   // 人工报修
}
