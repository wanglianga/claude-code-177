package com.community.water.kafka.msg;

/** 通知消息（物业/居民/师傅/运营） */
public record NotificationMessage(
        String targetType,
        String targetRef,
        String type,
        String content
) {}
