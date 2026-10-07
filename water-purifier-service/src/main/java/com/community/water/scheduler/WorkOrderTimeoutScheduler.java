package com.community.water.scheduler;

import com.community.water.config.WaterRules;
import com.community.water.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 工单超时巡检：师傅未按时到场 → 预警 + 改派 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderTimeoutScheduler {

    private final WorkOrderService workOrderService;
    private final WaterRules rules;

    @Scheduled(fixedDelayString = "#{${water.workorder.check-interval-seconds:30} * 1000}",
            initialDelay = 15000)
    public void checkOverdueOrders() {
        int escalated = workOrderService.escalateOverdueOrders();
        if (escalated > 0) {
            log.info("escalated {} overdue work orders", escalated);
        }
    }
}
