package com.community.water.domain;

public enum TicketStatus {
    OPEN,              // 已报修，未派单
    ASSIGNED,          // 已派单，待到场
    TECHNICIAN_LATE,   // 超过 SLA 未到场
    IN_PROGRESS,       // 师傅到场作业中
    AWAITING_RETEST,   // 换芯完成，待复检
    COMPLETED,         // 复检通过，链路关闭
    CANCELLED
}
