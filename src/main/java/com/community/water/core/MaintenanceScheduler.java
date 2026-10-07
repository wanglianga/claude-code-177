package com.community.water.core;

import com.community.water.domain.FulfillmentCase;
import com.community.water.repo.Repositories.FulfillmentCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 定时：师傅到场 SLA 扫描；对复检通过后的未闭环链路做收费/发票对账并自动闭环。 */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final TicketService ticketService;
    private final MaintenanceExecutionService executionService;
    private final FulfillmentCaseRepository caseRepository;

    public MaintenanceScheduler(TicketService ticketService,
                                MaintenanceExecutionService executionService,
                                FulfillmentCaseRepository caseRepository) {
        this.ticketService = ticketService;
        this.executionService = executionService;
        this.caseRepository = caseRepository;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.late-scan-ms:60000}")
    @Transactional
    public void scanLateArrivals() {
        int n = ticketService.scanOverdue();
        if (n > 0) {
            log.info("检测到 {} 张师傅未按时到场工单，已升级", n);
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduler.reconcile-ms:10000}")
    @Transactional
    public void reconcileCases() {
        for (FulfillmentCase c : caseRepository.findByStageNot("CLOSED")) {
            executionService.tryClose(c, "收费与发票对账完成，自动闭环");
        }
    }
}
