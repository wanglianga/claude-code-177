package com.community.water.domain;

public enum DeviceStatus {
    ACTIVE,       // 正常售水
    SUSPENDED,    // 水质异常暂停售水
    MAINTENANCE,  // 换芯维护中
    DECOMMISSIONED
}
