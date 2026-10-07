package com.community.water.kafka.msg;

/** 预警事件消息 */
public record AlertMessage(
        String alertNo,
        String deviceNo,
        String type,
        String level,
        String message
) {}
