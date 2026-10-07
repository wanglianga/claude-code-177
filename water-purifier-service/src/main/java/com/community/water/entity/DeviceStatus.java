package com.community.water.entity;

public enum DeviceStatus {
    /** 正常售水 */
    NORMAL,
    /** 暂停售水（水质异常/投诉聚集/滤芯耗尽等） */
    PAUSED,
    /** 维护中（师傅已到场/换芯中） */
    MAINTENANCE
}
