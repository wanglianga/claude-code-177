package com.community.water.entity;

public enum WorkOrderStatus {
    PENDING,
    ASSIGNED,
    ARRIVED,
    COMPLETED,
    VERIFIED,
    /** 超时未到场被关闭（已改派新单） */
    ESCALATED,
    CANCELLED
}
