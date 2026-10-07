package com.community.water.core;

import com.community.water.config.AppProperties;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.Requests.RegulatoryAuditRequest;
import com.community.water.web.dto.Requests.ReplaceFilterRequest;
import com.community.water.web.dto.Requests.RetestRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;

/**
 * 维护执行：师傅扫码换芯（旧芯编号/新芯批次/照片/冲洗时间）、复检、监管抽查与履约闭环。
 * 铁律：复检不合格绝不恢复售水；只有"复检通过 + 费用结清 + 发票无误"才关闭链路。
 */
@Service
public class MaintenanceExecutionService {

    private final MaintenanceTicketRepository ticketRepository;
    private final FilterReplacementRepository replacementRepository;
    private final WaterFilterRepository filterRepository;
    private final DeviceRepository deviceRepository;
    private final RetestRepository retestRepository;
    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final ComplaintRepository complaintRepository;
    private final FulfillmentCaseRepository caseRepository;
    private final CaseService caseService;
    private final DeviceService deviceService;
    private final TicketService ticketService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final EventBus eventBus;
    private final AppProperties props;

    public MaintenanceExecutionService(MaintenanceTicketRepository ticketRepository,
                                       FilterReplacementRepository replacementRepository,
                                       WaterFilterRepository filterRepository,
                                       DeviceRepository deviceRepository,
                                       RetestRepository retestRepository,
                                       ChargeRepository chargeRepository,
                                       InvoiceRepository invoiceRepository,
                                       ComplaintRepository complaintRepository,
                                       FulfillmentCaseRepository caseRepository,
                                       CaseService caseService,
                                       DeviceService deviceService,
                                       TicketService ticketService,
                                       AlertService alertService,
                                       NotificationService notificationService,
                                       EventBus eventBus,
                                       AppProperties props) {
        this.ticketRepository = ticketRepository;
        this.replacementRepository = replacementRepository;
        this.filterRepository = filterRepository;
        this.deviceRepository = deviceRepository;
        this.retestRepository = retestRepository;
        this.chargeRepository = chargeRepository;
        this.invoiceRepository = invoiceRepository;
        this.complaintRepository = complaintRepository;
        this.caseRepository = caseRepository;
        this.caseService = caseService;
        this.deviceService = deviceService;
        this.ticketService = ticketService;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.eventBus = eventBus;
        this.props = props;
    }

    /** 师傅扫码确认换芯。 */
    @Transactional
    public FilterReplacement replaceFilter(ReplaceFilterRequest req, Long actingTechnicianId) {
        MaintenanceTicket ticket = ticketService.require(req.ticketNo());
        if (ticket.getTechnicianId() == null || !ticket.getTechnicianId().equals(actingTechnicianId)) {
            throw new ApiException(403, "该工单未指派给当前师傅，不能扫码换芯");
        }
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw ApiException.conflict("工单状态 " + ticket.getStatus() + "，请先到场确认（IN_PROGRESS）再换芯");
        }
        if (req.flushMinutes() < props.flushMinMinutes()) {
            throw ApiException.badRequest("冲洗时间 " + req.flushMinutes() + " 分钟不足，要求至少 "
                    + props.flushMinMinutes() + " 分钟，保障炭粉/保护液排净");
        }
        Device device = deviceService.requireById(ticket.getDeviceId());
        WaterFilter old = filterRepository
                .findFirstByDeviceIdAndStatusOrderByInstalledAtDesc(device.getId(), FilterStatus.IN_USE)
                .orElseThrow(() -> ApiException.conflict("设备无在用滤芯，无法确认旧芯编号"));
        if (!old.getFilterSerialNo().equals(req.oldFilterSerialNo())) {
            throw ApiException.conflict("旧滤芯编号不匹配：设备当前滤芯 " + old.getFilterSerialNo()
                    + "，扫码为 " + req.oldFilterSerialNo());
        }
        if (filterRepository.findByFilterSerialNo(req.newFilterSerialNo()).isPresent()) {
            throw ApiException.badRequest("新滤芯编号已存在，不能重复安装：" + req.newFilterSerialNo());
        }

        // 旧芯拆下
        old.setStatus(FilterStatus.REMOVED);
        old.setRemovedAt(OffsetDateTime.now());
        filterRepository.save(old);

        // 新芯安装
        WaterFilter neu = new WaterFilter();
        neu.setDeviceId(device.getId());
        neu.setFilterSerialNo(req.newFilterSerialNo());
        neu.setBatchNo(req.newFilterBatchNo());
        neu.setInstalledAt(OffsetDateTime.now());
        neu.setExpectedCapacityLiters(req.newFilterCapacityLiters());
        neu.setUsedLiters(0.0);
        neu.setLifePercent(100);
        neu.setStatus(FilterStatus.IN_USE);
        filterRepository.save(neu);
        device.setCurrentFilterId(neu.getId());
        deviceRepository.save(device);

        FilterReplacement r = new FilterReplacement();
        r.setTicketId(ticket.getId());
        r.setDeviceId(device.getId());
        r.setDeviceCode(device.getDeviceCode());
        r.setTechnicianId(ticket.getTechnicianId());
        r.setTechnicianName(ticket.getTechnicianName());
        r.setOldFilterSerialNo(req.oldFilterSerialNo());
        r.setNewFilterSerialNo(req.newFilterSerialNo());
        r.setNewFilterBatchNo(req.newFilterBatchNo());
        r.setInstallPhotoUrl(req.installPhotoUrl());
        r.setFlushMinutes(req.flushMinutes());
        r.setReplacedAt(OffsetDateTime.now());
        r.setRetestResult(RetestResult.PENDING);
        r.setNewFilterId(neu.getId());
        r.setCaseId(ticket.getCaseId());
        replacementRepository.save(r);

        ticket.setStatus(TicketStatus.AWAITING_RETEST);
        ticket.setFilterReplacementId(r.getId());
        ticket.setUpdatedAt(OffsetDateTime.now());
        ticketRepository.save(ticket);

        FulfillmentCase c = ticket.getCaseId() == null ? null
                : caseRepository.findById(ticket.getCaseId()).orElse(null);
        if (c != null) {
            c.setStage("REPLACED");
            caseRepository.save(c);
            notificationService.notifyProperty(device, "【换芯完成待复检】" + device.getDeviceCode(),
                    "师傅 " + ticket.getTechnicianName() + " 已更换滤芯（新批次 " + req.newFilterBatchNo()
                            + "），冲洗 " + req.flushMinutes() + " 分钟，安装照片已上传，等待水质复检。", c, ticket);
        }
        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.FILTER_REPLACED, device.getDeviceCode(),
                EventBus.payload("ticketNo", ticket.getTicketNo(), "deviceCode", device.getDeviceCode(),
                        "oldSerial", req.oldFilterSerialNo(), "newSerial", req.newFilterSerialNo(),
                        "batch", req.newFilterBatchNo(), "flushMinutes", req.flushMinutes(),
                        "photo", req.installPhotoUrl(),
                        "caseNo", c == null ? null : c.getCaseNo()));
        return r;
    }

    /** 换芯后复检。合格才恢复售水并尝试闭环；不合格重新挂单且继续停机。 */
    @Transactional
    public WaterQualityRetest retest(RetestRequest req, String inspector) {
        MaintenanceTicket ticket = ticketService.require(req.ticketNo());
        if (ticket.getStatus() != TicketStatus.AWAITING_RETEST) {
            throw ApiException.conflict("工单状态 " + ticket.getStatus() + "，当前不允许复检");
        }
        FilterReplacement r = replacementRepository.findByTicketId(ticket.getId())
                .orElseGet(() -> replacementRepository
                        .findByDeviceIdOrderByReplacedAtDesc(ticket.getDeviceId()).stream()
                        .findFirst()
                        .orElseThrow(() -> ApiException.conflict("设备缺少换芯记录，不能复检")));
        Device device = deviceService.requireById(ticket.getDeviceId());
        boolean pass = req.tds() <= device.getTdsLimit()
                && req.chlorine() <= device.getChlorineLimit()
                && req.flowRate() > 0;

        WaterQualityRetest retest = saveRetest(device, ticket, r, req, pass, "POST_REPLACEMENT", inspector);
        r.setRetestResult(pass ? RetestResult.PASS : RetestResult.FAIL);
        r.setRetestedAt(OffsetDateTime.now());
        r.setRetestTds(req.tds());
        r.setRetestChlorine(req.chlorine());
        r.setRetestNote(req.note());
        replacementRepository.save(r);

        FulfillmentCase c = ticket.getCaseId() == null ? null
                : caseRepository.findById(ticket.getCaseId()).orElse(null);

        if (pass) {
            ticket.setStatus(TicketStatus.COMPLETED);
            ticket.setUpdatedAt(OffsetDateTime.now());
            ticketRepository.save(ticket);
            deviceService.resume(device, "换芯复检合格 TDS=" + req.tds() + " 余氯=" + req.chlorine(), c);
            if (c != null) {
                c.setRetestPassed(true);
                c.setStage("RETEST_PASSED");
                caseRepository.save(c);
            }
            alertService.resolve(device, List.of(AlertType.LIFE_WARNING, AlertType.EARLY_EXHAUSTION,
                    AlertType.WATER_QUALITY, AlertType.COMPLAINT_CLUSTER, AlertType.SMELL_AFTER_REPLACE,
                    AlertType.TECHNICIAN_LATE));
            complaintRepository.findByCaseId(ticket.getCaseId()).forEach(cmp -> {
                cmp.setStatus(ComplaintStatus.RESOLVED);
                cmp.setResolvedAt(OffsetDateTime.now());
                complaintRepository.save(cmp);
            });
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.RETEST_PASSED, device.getDeviceCode(),
                    EventBus.payload("ticketNo", ticket.getTicketNo(), "deviceCode", device.getDeviceCode(),
                            "tds", req.tds(), "chlorine", req.chlorine(),
                            "caseNo", c == null ? null : c.getCaseNo()));
            if (c != null) {
                tryClose(c, "换芯复检合格，售水已恢复");
            }
        } else {
            // 复检失败：本次换芯作业留痕完成（结果 FAIL），不恢复售水，同一履约链路下重新挂单处置
            ticket.setStatus(TicketStatus.COMPLETED);
            ticket.setUpdatedAt(OffsetDateTime.now());
            ticketRepository.save(ticket);
            String reason = String.format("换芯后复检不合格：TDS=%.1f（限值 %d），余氯=%.2f（限值 %.1f）",
                    req.tds(), device.getTdsLimit(), req.chlorine(), device.getChlorineLimit());
            deviceService.suspend(device, reason, c);
            MaintenanceTicket reopen = ticketService.openTicket(device, TicketTrigger.SMELL_AFTER_REPLACE,
                    reason + (req.note() == null ? "" : "；" + req.note()), c);
            // 复检失败多为冲洗不充分/管路残留，同一师傅继续处理，直接进入待复检队列
            reopen.setTechnicianId(ticket.getTechnicianId());
            reopen.setTechnicianName(ticket.getTechnicianName());
            reopen.setAssignedAt(OffsetDateTime.now());
            reopen.setArrivedAt(OffsetDateTime.now());
            reopen.setStatus(TicketStatus.AWAITING_RETEST);
            reopen.setFilterReplacementId(r.getId());
            ticketRepository.save(reopen);
            alertService.open(device, AlertType.SMELL_AFTER_REPLACE, reason, c, reopen, null, null);
            notificationService.notifyProperty(device, "【复检不合格继续停机】" + device.getDeviceCode(),
                    reason + "，已生成复查工单 " + reopen.getTicketNo() + "，合格前不会恢复售水。", c, reopen);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.RETEST_FAILED, device.getDeviceCode(),
                    EventBus.payload("ticketNo", ticket.getTicketNo(), "reopenTicketNo", reopen.getTicketNo(),
                            "tds", req.tds(), "chlorine", req.chlorine(),
                            "caseNo", c == null ? null : c.getCaseNo()));
        }
        return retest;
    }

    private WaterQualityRetest saveRetest(Device device, MaintenanceTicket ticket, FilterReplacement r,
                                          RetestRequest req, boolean pass, String type, String inspector) {
        WaterQualityRetest retest = new WaterQualityRetest();
        retest.setDeviceId(device.getId());
        retest.setTicketId(ticket.getId());
        retest.setReplacementId(r.getId());
        retest.setRetestType(type);
        retest.setTds(req.tds());
        retest.setChlorine(req.chlorine());
        retest.setFlowRate(req.flowRate());
        retest.setResult(pass ? RetestResult.PASS : RetestResult.FAIL);
        retest.setNote(req.note());
        retest.setInspector(inspector);
        return retestRepository.save(retest);
    }

    /** 监管抽查水质记录：不合格同样停机并纳入履约链路，留痕备查。 */
    @Transactional
    public WaterQualityRetest regulatoryAudit(RegulatoryAuditRequest req) {
        Device device = deviceService.requireByCode(req.deviceCode());
        boolean pass = req.tds() <= device.getTdsLimit()
                && req.chlorine() <= device.getChlorineLimit()
                && req.flowRate() > 0;

        WaterQualityRetest retest = new WaterQualityRetest();
        retest.setDeviceId(device.getId());
        retest.setRetestType("REGULATORY");
        retest.setTds(req.tds());
        retest.setChlorine(req.chlorine());
        retest.setFlowRate(req.flowRate());
        retest.setResult(pass ? RetestResult.PASS : RetestResult.FAIL);
        retest.setNote(req.note());
        retest.setInspector(req.inspector());
        retestRepository.save(retest);

        FulfillmentCase c = caseService.findOpenCase(device.getId());
        if (!pass) {
            String reason = String.format("监管抽查不合格：TDS=%.1f（限值 %d），余氯=%.2f（限值 %.1f），检查人 %s",
                    req.tds(), device.getTdsLimit(), req.chlorine(), device.getChlorineLimit(), req.inspector());
            c = caseService.obtainOpenCase(device, TicketTrigger.REGULATORY_AUDIT.name(), reason);
            Alert alert = alertService.open(device, AlertType.REGULATORY_AUDIT, reason, c, null, null, null);
            deviceService.suspend(device, reason, c);
            MaintenanceTicket ticket = ticketService.openTicket(device, TicketTrigger.REGULATORY_AUDIT, reason, c);
            notificationService.notifyProperty(device, "【监管抽查不合格已停机】" + device.getDeviceCode(),
                    reason + "，工单 " + ticket.getTicketNo() + "，处置复检记录将存档备查。", c, ticket);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.REGULATORY_AUDIT, device.getDeviceCode(),
                    EventBus.payload("deviceCode", device.getDeviceCode(), "result", "FAIL",
                            "inspector", req.inspector(), "caseNo", c.getCaseNo(),
                            "ticketNo", ticket.getTicketNo(), "alertId", alert.getId()));
        } else {
            notificationService.notifyProperty(device, "【监管抽查合格】" + device.getDeviceCode(),
                    "监管抽查合格：TDS=" + req.tds() + "，余氯=" + req.chlorine() + "，检查人 " + req.inspector()
                            + "，记录已存档。", c, null);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.REGULATORY_AUDIT, device.getDeviceCode(),
                    EventBus.payload("deviceCode", device.getDeviceCode(), "result", "PASS",
                            "inspector", req.inspector(), "caseNo", c == null ? null : c.getCaseNo()));
        }
        return retest;
    }

    /**
     * 重新计算链路的收费与发票状态并在满足条件时闭环：
     * 复检通过 + 全部收费成功 + 每张成功收费都有有效（非仅冲红）发票。
     */
    @Transactional
    public FulfillmentCase tryClose(FulfillmentCase c, String note) {
        if (c == null || "CLOSED".equals(c.getStage())) {
            return c;
        }
        List<Charge> charges = chargeRepository.findByCaseId(c.getId());
        boolean billingSettled = charges.stream()
                .allMatch(ch -> ch.getStatus() == ChargeStatus.SUCCESS || ch.getStatus() == ChargeStatus.WAIVED);
        boolean invoiceCorrect = charges.stream()
                .filter(ch -> ch.getStatus() == ChargeStatus.SUCCESS)
                .allMatch(this::hasValidInvoice);
        c.setBillingSettled(billingSettled);
        c.setInvoiceCorrect(invoiceCorrect);

        // 发生过停机（水质/异味）的链路必须复检通过；纯费用分摊链路无此要求
        boolean safetyOk = !c.isWaterSuspended() || c.isRetestPassed();
        if (safetyOk && billingSettled && invoiceCorrect) {
            c.setStage("CLOSED");
            c.setClosedAt(OffsetDateTime.now());
            c.setCloseNote(note);
            caseRepository.save(c);
            Device device = deviceService.requireById(c.getDeviceId());
            notificationService.notifyProperty(device, "【履约链路闭环】" + c.getCaseNo(),
                    "换芯复检合格、费用已结清、发票无误，链路 " + c.getCaseNo() + " 已闭环。", c, null);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.CASE_CLOSED, c.getCaseNo(),
                    EventBus.payload("caseNo", c.getCaseNo(), "deviceCode", c.getDeviceCode(),
                            "charges", charges.size(), "note", note));
        } else {
            caseRepository.save(c);
        }
        return c;
    }

    private boolean hasValidInvoice(Charge charge) {
        List<Invoice> invoices = invoiceRepository.findByChargeId(charge.getId());
        return invoices.stream().anyMatch(i -> i.getStatus() == InvoiceStatus.ISSUED
                || i.getStatus() == InvoiceStatus.REISSUED);
    }
}
