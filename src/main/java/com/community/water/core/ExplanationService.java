package com.community.water.core;

import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向居民的费用/暂停解释：把"为什么暂停、为什么收费"讲清楚，
 * 并回指同一设备履约链路与处置进度，避免设备继续出水但责任不清。
 */
@Service
public class ExplanationService {

    private final DeviceRepository deviceRepository;
    private final MaintenanceTicketRepository ticketRepository;
    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final FulfillmentCaseRepository caseRepository;
    private final FilterReplacementRepository replacementRepository;

    public ExplanationService(DeviceRepository deviceRepository,
                              MaintenanceTicketRepository ticketRepository,
                              ChargeRepository chargeRepository,
                              InvoiceRepository invoiceRepository,
                              FulfillmentCaseRepository caseRepository,
                              FilterReplacementRepository replacementRepository) {
        this.deviceRepository = deviceRepository;
        this.ticketRepository = ticketRepository;
        this.chargeRepository = chargeRepository;
        this.invoiceRepository = invoiceRepository;
        this.caseRepository = caseRepository;
        this.replacementRepository = replacementRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> explainDevice(String deviceCode) {
        Device d = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceCode));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceCode", d.getDeviceCode());
        m.put("location", d.getLocation());
        m.put("status", d.getStatus().name());
        m.put("selling", d.getStatus() == DeviceStatus.ACTIVE);
        m.put("pricePerLiter", d.getPricePerLiter().toPlainString());
        if (d.getStatus() == DeviceStatus.ACTIVE) {
            m.put("why", "设备运行正常，按 " + d.getPricePerLiter() + " 元/升 实时扣费，可正常扫码取水。");
        } else {
            m.put("why", "为保障公共饮水安全，本机因水质/异味风险暂停取水。");
            m.put("suspensionReason", d.getSuspensionReason());
            m.put("suspendedSince", d.getSuspendedAt());
            m.put("resumeCondition", "师傅换芯并冲洗充分后，水质复检 TDS≤" + d.getTdsLimit()
                    + "、余氯≤" + d.getChlorineLimit() + " 才会恢复售水，暂停期间不会扣费。");
        }
        FulfillmentCase c = caseRepository.findByDeviceIdOrderByCreatedAtDesc(d.getId()).stream().findFirst().orElse(null);
        if (c != null) {
            m.put("caseNo", c.getCaseNo());
            m.put("caseStage", c.getStage());
            List<MaintenanceTicket> ts = ticketRepository.findByCaseId(c.getId());
            if (ts.isEmpty()) {
                ts = ticketRepository.findByDeviceIdOrderByCreatedAtDesc(d.getId()).stream().limit(1).toList();
            }
            ts.stream().findFirst().ifPresent(t -> {
                m.put("latestTicketNo", t.getTicketNo());
                m.put("latestTicketStatus", t.getStatus().name());
                m.put("technician", t.getTechnicianName());
                m.put("dueAt", t.getDueAt());
            });
            replacementRepository.findByCaseId(c.getId()).stream().findFirst().ifPresent(r -> {
                m.put("lastNewFilterBatch", r.getNewFilterBatchNo());
                m.put("lastRetestResult", r.getRetestResult().name());
            });
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> explainCharge(String chargeNo, String accountNo) {
        Charge ch = chargeRepository.findByChargeNo(chargeNo)
                .orElseThrow(() -> ApiException.notFound("收费记录不存在: " + chargeNo));
        if (accountNo != null && !accountNo.equals(ch.getAccountNo())) {
            throw new ApiException(403, "只能查询本人账户的收费记录");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chargeNo", ch.getChargeNo());
        m.put("type", ch.getType().name());
        m.put("amount", ch.getAmount().toPlainString());
        m.put("status", ch.getStatus().name());
        m.put("building", ch.getBuilding());
        m.put("createdAt", ch.getCreatedAt());
        if (ch.getType() == ChargeType.WATER_FEE) {
            m.put("why", "扫码取水按升实时扣费，金额 = 单价 × 取水量。");
            m.put("formula", "单价(元/升) × 取水量(升)");
        } else {
            m.put("why", "本楼栋发生滤芯更换维护，经物业确认按楼栋住户均摊维护成本，每户金额一致（尾差并入末户）。");
            m.put("formula", "维护费总额 ÷ 本楼栋住户数");
        }
        m.put("reason", ch.getReason());
        m.put("attempts", ch.getAttempts());
        m.put("failReason", ch.getFailReason());
        List<Invoice> invoices = invoiceRepository.findByChargeId(ch.getId());
        m.put("invoices", invoices.stream().map(i -> Map.of(
                "invoiceNo", i.getInvoiceNo(), "title", i.getTitle(), "taxNo", i.getTaxNo(),
                "amount", i.getAmount().toPlainString(), "status", i.getStatus().name())).toList());
        if (ch.getCaseId() != null) {
            caseRepository.findById(ch.getCaseId()).ifPresent(c -> {
                m.put("caseNo", c.getCaseNo());
                m.put("caseStage", c.getStage());
                m.put("retestPassed", c.isRetestPassed());
            });
        }
        return m;
    }
}
