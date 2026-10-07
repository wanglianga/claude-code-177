package com.community.water;

import com.community.water.core.*;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.CaseView;
import com.community.water.web.dto.Requests.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核心履约链路测试，覆盖题目八类异常情形与统一链路：
 * 水质异常停机、师傅迟到、扫码换芯留痕、复检恢复/失败、
 * 寿命预警/提前耗尽、投诉聚集、换芯后异味、扣费失败、楼栋分摊、发票更正、监管抽查。
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceFlowIntegrationTest {

    @Autowired private SetupService setup;
    @Autowired private TelemetryService telemetryService;
    @Autowired private ComplaintService complaintService;
    @Autowired private TicketService ticketService;
    @Autowired private MaintenanceExecutionService executionService;
    @Autowired private ChargeService chargeService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private CaseService caseService;
    @Autowired private CaseQueryService caseQueryService;
    @Autowired private DispenseService dispenseService;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private MaintenanceTicketRepository ticketRepository;
    @Autowired private WaterFilterRepository filterRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private ChargeRepository chargeRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private TechnicianRepository technicianRepository;

    private long seq = 0;

    private static TelemetryRequest tel(String code, int life, double tds, double chlorine, double output) {
        return new TelemetryRequest(code, OffsetDateTime.now(), life, output, tds, chlorine,
                2.0, "0", OffsetDateTime.now());
    }

    private static TelemetryRequest telFault(String code, int life, double tds, double chlorine, String fault) {
        return new TelemetryRequest(code, OffsetDateTime.now(), life, 10.0, tds, chlorine,
                2.0, fault, OffsetDateTime.now());
    }

    private Device newDeviceWithAccounts(String code, BigDecimal... balances) {
        seq++;
        Community c = setup.createCommunity("测试小区-" + code, "测试路 " + code);
        Device d = setup.createDevice(new CreateDeviceRequest(code, c.getId(), code + "号楼大堂", code + "号楼"));
        setup.installInitialFilter(d.getId(), new CreateFilterRequest("FLT-" + code, "BATCH-A", 10000.0));
        setup.createTechnician("师傅-" + code, "138" + seq, c.getId());
        int i = 0;
        for (BigDecimal b : balances) {
            setup.createAccount("ACC-" + code + "-" + i, "居民" + i, c.getId(),
                    code + "号楼", "139" + seq + i, b);
            i++;
        }
        return deviceRepository.findByDeviceCode(code).orElseThrow();
    }

    private Long technicianFor(Device d) {
        return technicianRepository.findByCommunityId(d.getCommunityId()).get(0).getId();
    }

    private MaintenanceTicket latestTicket(Device d) {
        return ticketRepository.findByDeviceIdOrderByCreatedAtDesc(d.getId()).get(0);
    }

    private void assignAndArrive(MaintenanceTicket t, Long techId) {
        ticketService.assign(t.getTicketNo(), techId);
        ticketService.arrive(t.getTicketNo(), "到场");
    }

    private void replaceAndRetest(MaintenanceTicket t, Long techId, String newSerial, String batch,
                                  double tds, double chlorine, String inspector) {
        WaterFilter old = filterRepository
                .findFirstByDeviceIdAndStatusOrderByInstalledAtDesc(t.getDeviceId(), FilterStatus.IN_USE)
                .orElseThrow();
        executionService.replaceFilter(new ReplaceFilterRequest(
                t.getTicketNo(), old.getFilterSerialNo(), newSerial, batch,
                "https://oss.example.com/" + newSerial + ".jpg", 15, 10000.0), techId);
        executionService.retest(new RetestRequest(t.getTicketNo(), tds, chlorine, 2.0, "复检"), inspector);
    }

    // ---------- 场景一：水质异常停机 → 暂停拒绝取水 → 派单迟到 → 扫码换芯 → 复检恢复闭环 ----------

    @Test
    void waterQuality_suspend_lateArrival_replace_retestPass_close() {
        Device d = newDeviceWithAccounts("WQ-T01", new BigDecimal("50"));
        Long techId = technicianFor(d);

        telemetryService.ingest(telFault("WQ-T01", 80, 60, 0.5, "E12"));
        Device after = deviceRepository.findByDeviceCode("WQ-T01").orElseThrow();
        assertEquals(DeviceStatus.SUSPENDED, after.getStatus());
        assertTrue(after.getSuspensionReason().contains("E12"));
        MaintenanceTicket ticket = latestTicket(d);
        assertEquals(TicketTrigger.WATER_QUALITY, ticket.getTrigger());
        assertTrue(alertRepository.findByDeviceIdAndStatus(d.getId(), AlertStatus.OPEN).stream()
                .anyMatch(a -> a.getType() == AlertType.WATER_QUALITY));

        // 暂停期间拒绝取水并给出解释
        ApiException noWater = assertThrows(ApiException.class,
                () -> dispenseService.dispense(
                        new DispenseRequest("WQ-T01", "ACC-WQ-T01-0", 2.0, false, null)));
        assertEquals(409, noWater.getStatus());
        assertTrue(noWater.getMessage().contains("暂停"));

        ticketService.assign(ticket.getTicketNo(), techId);
        ticketService.backdateDue(ticket.getTicketNo(),
                OffsetDateTime.now().minusHours(30), OffsetDateTime.now().minusHours(6));
        assertTrue(ticketService.scanOverdue() >= 1);
        assertEquals(TicketStatus.TECHNICIAN_LATE, ticketService.require(ticket.getTicketNo()).getStatus());

        ticketService.arrive(ticket.getTicketNo(), "到场");
        // 旧芯编号不匹配必须拦截
        ApiException wrongSerial = assertThrows(ApiException.class,
                () -> executionService.replaceFilter(new ReplaceFilterRequest(
                        ticket.getTicketNo(), "FLT-FAKE", "NEW-T01", "BATCH-NEW",
                        "https://oss.example.com/x.jpg", 15, 10000.0), techId));
        assertEquals(409, wrongSerial.getStatus());
        // 冲洗时间不足必须拦截
        WaterFilter old = filterRepository
                .findFirstByDeviceIdAndStatusOrderByInstalledAtDesc(d.getId(), FilterStatus.IN_USE).orElseThrow();
        ApiException shortFlush = assertThrows(ApiException.class,
                () -> executionService.replaceFilter(new ReplaceFilterRequest(
                        ticket.getTicketNo(), old.getFilterSerialNo(), "NEW-T01", "BATCH-NEW",
                        "https://oss.example.com/x.jpg", 3, 10000.0), techId));
        assertEquals(400, shortFlush.getStatus());

        replaceAndRetest(ticket, techId, "NEW-T01", "BATCH-NEW", 30, 0.4, "质检员");
        assertEquals(DeviceStatus.ACTIVE, deviceRepository.findByDeviceCode("WQ-T01").orElseThrow().getStatus());

        CaseView view = caseQueryService.latestByDevice("WQ-T01");
        assertEquals("CLOSED", view.stage());
        assertTrue(view.retestPassed());
        assertEquals(1, view.replacements().size());
        assertTrue(view.replacements().get(0).installPhotoUrl().contains("NEW-T01"));
        assertTrue(view.replacements().get(0).oldFilterSerialNo().equals("FLT-WQ-T01"));
        assertTrue(view.timeline().stream().anyMatch(s -> s.contains("换芯") && s.contains("复检=PASS")));
        assertTrue(view.timeline().stream().anyMatch(s -> s.contains("迟到")));
    }

    // ---------- 场景二：寿命接近阈值生成预警并安排师傅（不停机） ----------

    @Test
    void lifeNearThreshold_createsWarning_butKeepsSelling() {
        Device d = newDeviceWithAccounts("WQ-T02", new BigDecimal("50"));
        telemetryService.ingest(tel("WQ-T02", 15, 40, 0.5, 5.0));
        assertEquals(DeviceStatus.ACTIVE, deviceRepository.findByDeviceCode("WQ-T02").orElseThrow().getStatus());
        assertTrue(alertRepository.findByDeviceIdAndStatus(d.getId(), AlertStatus.OPEN).stream()
                .anyMatch(a -> a.getType() == AlertType.LIFE_WARNING));
        assertEquals(1, ticketRepository.findByDeviceIdOrderByCreatedAtDesc(d.getId()).size());
        // 正常取水不受影响且能开票
        DispenseRecord rec = dispenseService.dispense(
                new DispenseRequest("WQ-T02", "ACC-WQ-T02-0", 10.0, false, null));
        assertTrue(rec.isChargeSucceeded());
        assertEquals(0, new BigDecimal("47.00").compareTo(rec.getBalanceAfter()));
    }

    // ---------- 场景三：滤芯寿命提前耗尽（水质差导致有效寿命归零，标称容量仍富余） ----------

    @Test
    void earlyExhaustion_flaggedWhenCapacityStillPlenty() {
        Device d = newDeviceWithAccounts("WQ-T03");
        telemetryService.ingest(telFault("WQ-T03", 30, 160, 3.0, "0"));
        WaterFilter exhausted = filterRepository.findByDeviceIdOrderByInstalledAtDesc(d.getId()).get(0);
        assertTrue(exhausted.isEarlyExhausted());
        assertEquals(FilterStatus.EXHAUSTED, exhausted.getStatus());
        assertTrue(alertRepository.findAll().stream()
                .anyMatch(a -> a.getDeviceId().equals(d.getId()) && a.getType() == AlertType.EARLY_EXHAUSTION));
        // 水质异常同时已停机
        assertEquals(DeviceStatus.SUSPENDED, deviceRepository.findByDeviceCode("WQ-T03").orElseThrow().getStatus());
    }

    // ---------- 场景四：扣费失败 → 充值 → 重试成功并开票，告警关闭 ----------

    @Test
    void paymentFailed_thenRetrySucceeds_andAlertResolved() {
        Device d = newDeviceWithAccounts("WQ-T04", BigDecimal.ZERO);
        DispenseRecord rec = dispenseService.dispense(
                new DispenseRequest("WQ-T04", "ACC-WQ-T04-0", 5.0, false, null));
        assertFalse(rec.isChargeSucceeded());
        Charge failed = chargeRepository.findById(rec.getChargeId()).orElseThrow();
        assertEquals(ChargeStatus.FAILED, failed.getStatus());
        Alert payAlert = alertRepository.findAll().stream()
                .filter(a -> a.getType() == AlertType.PAYMENT_FAILED
                        && failed.getId().equals(a.getChargeId()))
                .findFirst().orElseThrow();

        setup.recharge("ACC-WQ-T04-0", new BigDecimal("10"));
        Charge retried = chargeService.retry(failed.getChargeNo());
        assertEquals(ChargeStatus.SUCCESS, retried.getStatus());
        assertEquals(2, retried.getAttempts());
        assertNotNull(retried.getInvoiceId());
        assertEquals(AlertStatus.HANDLED, alertRepository.findById(payAlert.getId()).orElseThrow().getStatus());
    }

    // ---------- 场景五：投诉聚集 → 停机报修 → 换芯复检恢复 ----------

    @Test
    void complaintCluster_suspendsAndRepairs() {
        Device d = newDeviceWithAccounts("WQ-T05", new BigDecimal("20"));
        Long techId = technicianFor(d);
        complaintService.registerResident("WQ-T05", "ACC-WQ-T05-0", "有异味1", null);
        complaintService.registerResident("WQ-T05", "ACC-WQ-T05-0", "有异味2", null);
        complaintService.registerResident("WQ-T05", "ACC-WQ-T05-0", "有异味3", null);

        Device after = deviceRepository.findByDeviceCode("WQ-T05").orElseThrow();
        assertEquals(DeviceStatus.SUSPENDED, after.getStatus());
        MaintenanceTicket t = latestTicket(d);
        assertEquals(TicketTrigger.COMPLAINT_CLUSTER, t.getTrigger());

        assignAndArrive(t, techId);
        replaceAndRetest(t, techId, "NEW-T05", "BATCH-NEW", 25, 0.3, "质检员");
        assertEquals(DeviceStatus.ACTIVE, deviceRepository.findByDeviceCode("WQ-T05").orElseThrow().getStatus());
        CaseView view = caseQueryService.latestByDevice("WQ-T05");
        assertEquals("CLOSED", view.stage());
    }

    // ---------- 场景六：换芯后仍有异味 → 停机；复检失败继续停机并重新挂单 ----------

    @Test
    void smellAfterReplace_keepsSuspended_andReopensTicket() {
        Device d = newDeviceWithAccounts("WQ-T06", new BigDecimal("20"));
        Long techId = technicianFor(d);
        complaintService.registerResident("WQ-T06", "ACC-WQ-T06-0", "异味1", null);
        complaintService.registerResident("WQ-T06", "ACC-WQ-T06-0", "异味2", null);
        complaintService.registerResident("WQ-T06", "ACC-WQ-T06-0", "异味3", null);
        MaintenanceTicket t1 = latestTicket(d);
        assignAndArrive(t1, techId);
        replaceAndRetest(t1, techId, "NEW-T06-A", "BATCH-NEW1", 20, 0.3, "质检员");
        assertEquals(DeviceStatus.ACTIVE, deviceRepository.findByDeviceCode("WQ-T06").orElseThrow().getStatus());

        // 换芯后 48h 内再次投诉即判定"换芯后仍有异味"，无需等满 3 起
        complaintService.registerResident("WQ-T06", "ACC-WQ-T06-0", "换完还是有味", null);
        assertEquals(DeviceStatus.SUSPENDED,
                deviceRepository.findByDeviceCode("WQ-T06").orElseThrow().getStatus());
        MaintenanceTicket t2 = latestTicket(d);
        assertEquals(TicketTrigger.SMELL_AFTER_REPLACE, t2.getTrigger());

        assignAndArrive(t2, techId);
        replaceAndRetest(t2, techId, "NEW-T06-B", "BATCH-NEW2", 130, 0.6, "质检员");
        assertEquals(DeviceStatus.SUSPENDED,
                deviceRepository.findByDeviceCode("WQ-T06").orElseThrow().getStatus());
        long smellAlerts = alertRepository.findAll().stream()
                .filter(a -> a.getDeviceId().equals(d.getId()) && a.getType() == AlertType.SMELL_AFTER_REPLACE)
                .count();
        assertTrue(smellAlerts >= 2);
        // 复查工单已自动进入待复检，师傅可再次冲洗复检，合格后才恢复
        assertTrue(ticketRepository.findByDeviceIdOrderByCreatedAtDesc(d.getId()).stream()
                .anyMatch(t -> t.getStatus() == TicketStatus.AWAITING_RETEST));
    }

    // ---------- 场景七：楼栋分摊（含欠费）→ 补缴 → 发票抬头更正 → 闭环 ----------

    @Test
    void buildingShare_failedHousehold_invoiceCorrection() {
        Device d = newDeviceWithAccounts("WQ-T07",
                new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("50"));
        Long techId = technicianFor(d);
        telemetryService.ingest(telFault("WQ-T07", 80, 150, 0.5, "E9"));
        MaintenanceTicket t = latestTicket(d);
        assignAndArrive(t, techId);
        replaceAndRetest(t, techId, "NEW-T07", "BATCH-NEW", 30, 0.4, "质检员");
        FulfillmentCase closed = caseService.findLatestCase(d.getId());
        assertEquals("CLOSED", closed.getStage());

        List<Charge> shares = chargeService.shareByBuilding(
                deviceRepository.findByDeviceCode("WQ-T07").orElseThrow(),
                new BigDecimal("10.00"), "换芯维护费", closed);
        caseService.reopenIfClosed(closed, "楼栋维护费分摊需对账");
        assertEquals(3, shares.size());
        long failed = shares.stream().filter(ch -> ch.getStatus() == ChargeStatus.FAILED).count();
        assertEquals(1, failed);
        BigDecimal sum = shares.stream().map(Charge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("10.00").compareTo(sum));

        Charge failedCharge = shares.stream()
                .filter(ch -> ch.getStatus() == ChargeStatus.FAILED).findFirst().orElseThrow();
        setup.recharge(failedCharge.getAccountNo(), new BigDecimal("10"));
        Charge paid = chargeService.retry(failedCharge.getChargeNo());
        assertEquals(ChargeStatus.SUCCESS, paid.getStatus());

        executionService.tryClose(caseService.requireByNo(closed.getCaseNo()), "费用结清");
        assertEquals("CLOSED", caseService.requireByNo(closed.getCaseNo()).getStage());

        Invoice issued = invoiceRepository.findByChargeId(paid.getId()).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.ISSUED).findFirst().orElseThrow();
        Invoice corrected = invoiceService.correctTitle(
                issued.getInvoiceNo(), "李四单位", "91370000XXXXXXXXX1");
        assertEquals(InvoiceStatus.VOIDED,
                invoiceRepository.findByInvoiceNo(issued.getInvoiceNo()).orElseThrow().getStatus());
        assertEquals(InvoiceStatus.REISSUED, corrected.getStatus());
        assertEquals(issued.getId(), corrected.getReissuedFromId());
        executionService.tryClose(caseService.requireByNo(closed.getCaseNo()), "发票更正完成");
        assertEquals("CLOSED", caseService.requireByNo(closed.getCaseNo()).getStage());
    }

    // ---------- 场景八：监管抽查不合格 → 停机纳入链路 ----------

    @Test
    void regulatoryAudit_failSuspendsAndLinks() {
        Device d = newDeviceWithAccounts("WQ-T08");
        executionService.regulatoryAudit(new RegulatoryAuditRequest(
                "WQ-T08", 120.0, 0.4, 2.0, "抽样检查", "监管员-赵"));
        assertEquals(DeviceStatus.SUSPENDED,
                deviceRepository.findByDeviceCode("WQ-T08").orElseThrow().getStatus());
        assertEquals(TicketTrigger.REGULATORY_AUDIT, latestTicket(d).getTrigger());
        assertTrue(alertRepository.findAll().stream()
                .anyMatch(a -> a.getDeviceId().equals(d.getId()) && a.getType() == AlertType.REGULATORY_AUDIT));
        CaseView view = caseQueryService.latestByDevice("WQ-T08");
        assertTrue(view.retests().stream().anyMatch(r -> r.retestType().equals("REGULATORY")
                && r.result().equals("FAIL") && r.inspector().equals("监管员-赵")));
    }

    // ---------- 场景九：运营动态调整阈值后预警点改变 ----------

    @Test
    void strategyThresholdChange_changesWarning() {
        Device d = newDeviceWithAccounts("WQ-T09");
        setup.updateStrategy("WQ-T09", new DeviceStrategyRequest(40, 100, 2.0, null));
        telemetryService.ingest(tel("WQ-T09", 35, 40, 0.5, 5.0));
        assertEquals(DeviceStatus.ACTIVE, deviceRepository.findByDeviceCode("WQ-T09").orElseThrow().getStatus());
        assertTrue(alertRepository.findAll().stream()
                .anyMatch(a -> a.getDeviceId().equals(d.getId()) && a.getType() == AlertType.LIFE_WARNING));
    }

    // ---------- 场景十：同一设备多次异常都挂入同一条履约链路 ----------

    @Test
    void multipleIncidents_shareSameFulfillmentCaseUntilClosed() {
        Device d = newDeviceWithAccounts("WQ-T10");
        // 第一次：水质异常（未闭环）
        telemetryService.ingest(telFault("WQ-T10", 80, 120, 0.5, "0"));
        FulfillmentCase first = caseService.findOpenCase(d.getId());
        assertNotNull(first);
        // 第二次：再来一条异常遥测，复用同一链路，不新建
        telemetryService.ingest(telFault("WQ-T10", 70, 130, 0.6, "E1"));
        FulfillmentCase second = caseService.findOpenCase(d.getId());
        assertEquals(first.getId(), second.getId());
        assertEquals(1, caseQueryService.byCaseNo(first.getCaseNo()).tickets().size());
    }
}
