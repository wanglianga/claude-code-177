package com.community.water.entity;

public enum AlertType {
    /** 滤芯寿命接近阈值 */
    FILTER_LIFE_LOW,
    /** 滤芯寿命提前耗尽 */
    FILTER_EXHAUSTED,
    /** 水质异常（TDS/余氯超标） */
    WATER_QUALITY_ABNORMAL,
    /** 水质投诉集中 */
    COMPLAINT_CLUSTER,
    /** 师傅未按时到场 */
    TECHNICIAN_NO_SHOW,
    /** 居民账户扣费失败 */
    CHARGE_FAILED,
    /** 换芯后仍有异味 */
    POST_REPLACEMENT_ODOR,
    /** 设备故障码上报 */
    DEVICE_FAULT
}
