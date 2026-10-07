package com.community.water.kafka.msg;

/** 取水事件消息（取水成功后广播，供计量/审计等下游消费） */
public record IntakeMessage(
        String intakeNo,
        String deviceNo,
        String accountNo,
        String community,
        String building,
        double amountLiters,
        String fee,
        boolean odorComplaint
) {}
