package com.community.water.domain;

/** 预警类型，覆盖题目中的全部异常情形。 */
public enum AlertType {
    LIFE_WARNING("滤芯寿命接近阈值"),
    EARLY_EXHAUSTION("滤芯寿命提前耗尽"),
    WATER_QUALITY("水质异常，暂停售水"),
    COMPLAINT_CLUSTER("异味投诉集中"),
    TECHNICIAN_LATE("师傅未按时到场"),
    SMELL_AFTER_REPLACE("换芯后仍有异味"),
    PAYMENT_FAILED("居民账户扣费失败"),
    REGULATORY_AUDIT("监管抽查水质记录");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
