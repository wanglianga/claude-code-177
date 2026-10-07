package com.community.water.core;

import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.CaseView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 把同一设备履约链路上的设备、滤芯、取水、投诉、报修、换芯、收费、发票、复检聚合为一个视图。 */
@Service
public class CaseQueryService {

    private final FulfillmentCaseRepository caseRepository;
    private final DeviceRepository deviceRepository;
    private final WaterFilterRepository filterRepository;
    private final TelemetryRepository telemetryRepository;
    private final DispenseRepository dispenseRepository;
    private final ComplaintRepository complaintRepository;
    private final MaintenanceTicketRepository ticketRepository;
    private final FilterReplacementRepository replacementRepository;
    private final RetestRepository retestRepository;
    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final AlertRepository alertRepository;
    private final NotificationRepository notificationRepository;

    public CaseQueryService(FulfillmentCaseRepository caseRepository, DeviceRepository deviceRepository,
                            WaterFilterRepository filterRepository, TelemetryRepository telemetryRepository,
                            DispenseRepository dispenseRepository, ComplaintRepository complaintRepository,
                            MaintenanceTicketRepository ticketRepository,
                            FilterReplacementRepository replacementRepository,
                            RetestRepository retestRepository, ChargeRepository chargeRepository,
                            InvoiceRepository invoiceRepository, AlertRepository alertRepository,
                            NotificationRepository notificationRepository) {
        this.caseRepository = caseRepository;
        this.deviceRepository = deviceRepository;
        this.filterRepository = filterRepository;
        this.telemetryRepository = telemetryRepository;
        this.dispenseRepository = dispenseRepository;
        this.complaintRepository = complaintRepository;
        this.ticketRepository = ticketRepository;
        this.replacementRepository = replacementRepository;
        this.retestRepository = retestRepository;
        this.chargeRepository = chargeRepository;
        this.invoiceRepository = invoiceRepository;
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public CaseView byCaseNo(String caseNo) {
        FulfillmentCase c = caseRepository.findByCaseNo(caseNo)
                .orElseThrow(() -> ApiException.notFound("履约链路不存在: " + caseNo));
        return build(c);
    }

    @Transactional(readOnly = true)
    public CaseView latestByDevice(String deviceCode) {
        Device d = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceCode));
        FulfillmentCase c = caseRepository.findByDeviceIdOrderByCreatedAtDesc(d.getId()).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("设备暂无履约链路: " + deviceCode));
        return build(c);
    }

    @Transactional(readOnly = true)
    public List<CaseView> byCommunity(Long communityId) {
        return caseRepository.findByCommunityIdOrderByCreatedAtDesc(communityId).stream().map(this::build).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseView> all() {
        return caseRepository.findAll().stream()
                .sorted(Comparator.comparing(FulfillmentCase::getCreatedAt).reversed())
                .map(this::build).toList();
    }

    private CaseView build(FulfillmentCase c) {
        Device device = deviceRepository.findById(c.getDeviceId()).orElse(null);
        WaterFilter currentFilter = filterRepository
                .findFirstByDeviceIdAndStatusOrderByInstalledAtDesc(c.getDeviceId(), FilterStatus.IN_USE)
                .orElse(null);

        List<TelemetryRecord> tel = telemetryRepository.findByDeviceIdOrderByEventTimeDesc(c.getDeviceId())
                .stream().limit(10).toList();
        List<DispenseRecord> disp = dispenseRepository.findByDeviceIdOrderByDispensedAtDesc(c.getDeviceId())
                .stream().limit(20).toList();
        List<Complaint> comp = complaintRepository.findByCaseId(c.getId());
        if (comp.isEmpty()) {
            comp = complaintRepository.findByDeviceIdOrderByCreatedAtDesc(c.getDeviceId()).stream().limit(20).toList();
        }
        List<MaintenanceTicket> tickets = ticketRepository.findByCaseId(c.getId());
        if (tickets.isEmpty()) {
            tickets = ticketRepository.findByDeviceIdOrderByCreatedAtDesc(c.getDeviceId()).stream().limit(10).toList();
        }
        List<FilterReplacement> reps = replacementRepository.findByCaseId(c.getId());
        if (reps.isEmpty()) {
            reps = replacementRepository.findByDeviceIdOrderByReplacedAtDesc(c.getDeviceId()).stream().limit(10).toList();
        }
        List<WaterQualityRetest> retests = retestRepository.findByDeviceIdOrderByCreatedAtDesc(c.getDeviceId())
                .stream().limit(20).toList();
        List<Charge> charges = chargeRepository.findByCaseId(c.getId());
        List<Invoice> invoices = collectInvoices(charges);
        List<Alert> alerts = alertRepository.findByCaseId(c.getId());
        if (alerts.isEmpty()) {
            alerts = alertRepository.findByDeviceIdOrderByCreatedAtDesc(c.getDeviceId()).stream().limit(20).toList();
        }
        List<Notification> notes = notificationRepository.findByDeviceIdOrderByCreatedAtDesc(c.getDeviceId())
                .stream().limit(30).toList();

        return new CaseView(
                c.getId(), c.getCaseNo(), c.getDeviceCode(), c.getCommunityId(), c.getBuilding(),
                c.getPrimaryReason(), c.getSummary(), c.getStage(), c.isWaterSuspended(),
                c.isRetestPassed(), c.isBillingSettled(), c.isInvoiceCorrect(),
                deviceInfo(device),
                currentFilter == null ? null : filterInfo(currentFilter),
                tel.stream().map(this::telInfo).toList(),
                disp.stream().map(this::dispInfo).toList(),
                comp.stream().map(x -> new CaseView.ComplaintInfo(x.getId(), x.getAccountNo(), x.getContent(),
                        x.getStatus().name(), fmt(x.getCreatedAt()))).toList(),
                tickets.stream().map(x -> new CaseView.TicketInfo(x.getId(), x.getTicketNo(),
                        x.getTrigger().name(), x.getTriggerDetail(), x.getStatus().name(),
                        x.getTechnicianName(), fmt(x.getAssignedAt()), fmt(x.getDueAt()), fmt(x.getArrivedAt()),
                        x.isLateAlerted())).toList(),
                reps.stream().map(x -> new CaseView.ReplacementInfo(x.getId(), x.getTechnicianName(),
                        x.getOldFilterSerialNo(), x.getNewFilterSerialNo(), x.getNewFilterBatchNo(),
                        x.getInstallPhotoUrl(), x.getFlushMinutes(), fmt(x.getReplacedAt()),
                        x.getRetestResult().name(), fmt(x.getRetestedAt()), x.getRetestTds(),
                        x.getRetestChlorine())).toList(),
                retests.stream().map(x -> new CaseView.RetestInfo(x.getRetestType(), x.getTds(),
                        x.getChlorine(), x.getFlowRate(), x.getResult().name(), x.getInspector(),
                        fmt(x.getCreatedAt()), x.getNote())).toList(),
                charges.stream().map(ch -> new CaseView.ChargeInfo(ch.getId(), ch.getChargeNo(),
                        ch.getType().name(), ch.getAccountNo(), ch.getAmount().toPlainString(),
                        ch.getStatus().name(), ch.getAttempts(), ch.getReason(), ch.getFailReason(),
                        invoiceNoFor(ch.getId(), invoices))).toList(),
                invoices.stream().map(i -> new CaseView.InvoiceInfo(i.getId(), i.getInvoiceNo(),
                        i.getTitle(), i.getTaxNo(), i.getAmount().toPlainString(), i.getStatus().name(),
                        i.getReissuedById())).toList(),
                alerts.stream().map(a -> new CaseView.AlertInfo(a.getId(), a.getType().name(),
                        a.getMessage(), a.getStatus().name(), fmt(a.getCreatedAt()), a.getTicketId())).toList(),
                notes.stream().map(n -> new CaseView.NotificationInfo(n.getTargetRole(), n.getChannel(),
                        n.getTitle(), n.getContent(), fmt(n.getCreatedAt()), n.isDelivered())).toList(),
                timeline(c, tickets, reps, retests, charges, comp, alerts));
    }

    private List<Invoice> collectInvoices(List<Charge> charges) {
        List<Invoice> all = new ArrayList<>();
        for (Charge ch : charges) {
            all.addAll(invoiceRepository.findByChargeId(ch.getId()));
        }
        return all;
    }

    private String invoiceNoFor(Long chargeId, List<Invoice> invoices) {
        return invoices.stream()
                .filter(i -> i.getChargeId().equals(chargeId)
                        && i.getStatus() != InvoiceStatus.VOIDED)
                .map(Invoice::getInvoiceNo).findFirst().orElse(null);
    }

    private CaseView.DeviceInfo deviceInfo(Device d) {
        if (d == null) {
            return null;
        }
        return new CaseView.DeviceInfo(d.getId(), d.getDeviceCode(), d.getStatus().name(),
                d.getLocation(), d.getBuilding(), d.getLifeThresholdPercent(), d.getTdsLimit(),
                d.getChlorineLimit(), d.getSuspensionReason(), d.getCumulativeOutputLiters());
    }

    private CaseView.FilterInfo filterInfo(WaterFilter f) {
        return new CaseView.FilterInfo(f.getFilterSerialNo(), f.getBatchNo(), f.getLifePercent(),
                f.getUsedLiters(), f.getExpectedCapacityLiters(), f.getStatus().name(),
                f.isEarlyExhausted(), fmt(f.getInstalledAt()));
    }

    private CaseView.TelemetryInfo telInfo(TelemetryRecord t) {
        boolean abnormal = false;
        Device d = deviceRepository.findById(t.getDeviceId()).orElse(null);
        if (d != null) {
            abnormal = t.getTds() > d.getTdsLimit() || t.getChlorine() > d.getChlorineLimit()
                    || (t.getFaultCode() != null && !t.getFaultCode().isBlank() && !"0".equals(t.getFaultCode()));
        }
        return new CaseView.TelemetryInfo(fmt(t.getEventTime()), t.getFilterLifePercent(),
                t.getWaterOutputLiters(), t.getTds(), t.getChlorine(), t.getFlowRate(), t.getFaultCode(),
                abnormal);
    }

    private CaseView.DispenseInfo dispInfo(DispenseRecord d) {
        return new CaseView.DispenseInfo(d.getId(), fmt(d.getDispensedAt()), d.getLiters(),
                d.getAmount().toPlainString(), d.getBalanceAfter().toPlainString(),
                d.isChargeSucceeded(), d.isSmellComplaint(), d.getAccountNo());
    }

    /** 时间线：把各环节按时间排序，证明"每次换芯是否真实完成、责任是否清晰"。 */
    private List<String> timeline(FulfillmentCase c, List<MaintenanceTicket> tickets,
                                  List<FilterReplacement> reps, List<WaterQualityRetest> retests,
                                  List<Charge> charges, List<Complaint> comps, List<Alert> alerts) {
        List<String> lines = new ArrayList<>();
        lines.add(fmt(c.getCreatedAt()) + " 履约链路 " + c.getCaseNo() + " 建立，原因：" + c.getPrimaryReason());
        alerts.forEach(a -> lines.add(fmt(a.getCreatedAt()) + " 预警[" + a.getType().name() + "] "
                + a.getMessage()));
        tickets.forEach(t -> lines.add(fmt(t.getCreatedAt()) + " 报修单 " + t.getTicketNo()
                + " 触发=" + t.getTrigger() + " 状态=" + t.getStatus()
                + (t.getTechnicianName() == null ? "" : " 师傅=" + t.getTechnicianName())
                + (t.isLateAlerted() ? "（迟到已催办）" : "")));
        comps.forEach(x -> lines.add(fmt(x.getCreatedAt()) + " 投诉[" + x.getStatus() + "] "
                + x.getAccountNo() + "：" + x.getContent()));
        reps.forEach(r -> lines.add(fmt(r.getReplacedAt()) + " 换芯 旧=" + r.getOldFilterSerialNo()
                + " 新=" + r.getNewFilterSerialNo() + " 批次=" + r.getNewFilterBatchNo()
                + " 冲洗=" + r.getFlushMinutes() + "min 照片=" + r.getInstallPhotoUrl()
                + " 复检=" + r.getRetestResult()));
        retests.forEach(r -> lines.add(fmt(r.getCreatedAt()) + " 水质复检[" + r.getRetestType() + "] "
                + r.getResult() + " TDS=" + r.getTds() + " 余氯=" + r.getChlorine()
                + " 检查人=" + r.getInspector()));
        charges.forEach(ch -> lines.add(fmt(ch.getCreatedAt()) + " 收费[" + ch.getType() + "] "
                + ch.getAccountNo() + " " + ch.getAmount() + "元 状态=" + ch.getStatus()
                + (ch.getFailReason() == null ? "" : "（" + ch.getFailReason() + "）")));
        if ("CLOSED".equals(c.getStage())) {
            lines.add(fmt(c.getClosedAt()) + " 链路闭环：复检通过、费用结清、发票无误。"
                    + (c.getCloseNote() == null ? "" : c.getCloseNote()));
        }
        return lines;
    }

    private String fmt(OffsetDateTime t) {
        return t == null ? "" : t.toString();
    }
}
