package com.community.water;

import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.kafka.KafkaProducerService;
import com.community.water.kafka.msg.TelemetryMessage;
import com.community.water.repository.*;
import com.community.water.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * 全链路集成测试：遥测 → 滤芯寿命 → 预警 → 工单 → 换芯 → 收费 → 发票 → 复检 → 履约链路。
 * 使用 Embedded Kafka + H2（PostgreSQL 兼容模式）。
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
        "water.device.telemetry", "water.device.intake", "water.alert", "water.notification"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WaterFlowIntegrationTest {

    @Autowired KafkaProducerService producer;
    @Autowired DeviceRepository deviceRepository;
    @Autowired FilterCartridgeRepository filterRepository;
    @Autowired DeviceTelemetryRepository telemetryRepository;
    @Autowired AlertRepository alertRepository;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired FilterReplacementRepository replacementRepository;
    @Autowired RecheckRecordRepository recheckRepository;
    @Autowired ResidentAccountRepository accountRepository;
    @Autowired ChargeRecordRepository chargeRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired DeviceEventRepository eventRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired IntakeService intakeService;
    @Autowired WorkOrderService workOrderService;
    @Autowired ReplacementService replacementService;
    @Autowired InvoiceService invoiceService;
    @Autowired OperationsService operationsService;

    private Device device(String no) {
        return deviceRepository.findByDeviceNo(no).orElseThrow();
    }

    private static void await(String what, Supplier<Boolean> cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(cond.get())) {
                return;
            }
            Thread.sleep(250);
        }
        fail("等待超时: " + what);
    }

    private void sendTelemetry(String deviceNo, double totalOutput, double tds, double chlorine) {
        producer.sendTelemetry(new TelemetryMessage(deviceNo, null, totalOutput,
                tds, chlorine, 1.5, null, LocalDateTime.now().minusDays(30)));
    }

    private long telemetryCount(String deviceNo) {
        return telemetryRepository.findByDeviceAndReportedAtBetweenOrderByReportedAt(
                device(deviceNo), LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)).size();
    }

    private FilterCartridge activeFilter(String deviceNo) {
        return filterRepository.findByDeviceAndStatus(device(deviceNo), FilterStatus.ACTIVE).orElseThrow();
    }

    private boolean hasAlert(String deviceNo, AlertType type) {
        return alertRepository.findByDeviceOrderByCreatedAtDesc(device(deviceNo)).stream()
                .anyMatch(a -> a.getType() == type);
    }

    // ---------- 1. 遥测经 Kafka 驱动滤芯寿命动态消耗 ----------

    @Test
    @Order(1)
    void telemetryThroughKafkaConsumesFilterLifeDynamically() throws Exception {
        sendTelemetry("DEV-001", 0, 40, 0.5);            // 基线
        await("DEV-001 基线遥测入库", () -> telemetryCount("DEV-001") >= 1);

        sendTelemetry("DEV-001", 100, 90, 0.5);          // +100L，TDS=90 → 系数 1.2
        await("DEV-001 滤芯消耗", () -> activeFilter("DEV-001").getUsedLiters() > 0);

        FilterCartridge filter = activeFilter("DEV-001");
        assertThat(filter.getUsedLiters()).isEqualTo(120.0);   // 100 × 1.2
        assertThat(filter.getLifePercent()).isEqualTo(98.8);   // 100 - 1.2
        assertThat(device("DEV-001").getStatus()).isEqualTo(DeviceStatus.NORMAL);
    }

    // ---------- 2. 滤芯寿命接近阈值 → 预警 + 自动派单 ----------

    @Test
    @Order(2)
    void lowFilterLifeRaisesAlertAndSchedulesReplacement() throws Exception {
        sendTelemetry("DEV-002", 0, 40, 0.5);
        await("DEV-002 基线遥测入库", () -> telemetryCount("DEV-002") >= 1);

        sendTelemetry("DEV-002", 8600, 40, 0.5);         // 寿命降至 14%
        await("DEV-002 滤芯寿命预警", () -> hasAlert("DEV-002", AlertType.FILTER_LIFE_LOW));

        FilterCartridge filter = activeFilter("DEV-002");
        assertThat(filter.getLifePercent()).isLessThanOrEqualTo(15);

        await("DEV-002 换芯工单", () -> workOrderRepository
                .findByDeviceOrderByCreatedAtDesc(device("DEV-002")).stream()
                .anyMatch(o -> o.getType() == WorkOrderType.FILTER_REPLACEMENT));
        WorkOrder order = workOrderRepository.findByDeviceOrderByCreatedAtDesc(device("DEV-002"))
                .stream().filter(o -> o.getType() == WorkOrderType.FILTER_REPLACEMENT)
                .findFirst().orElseThrow();
        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(order.getTechnician()).isNotNull();
        assertThat(order.getDeadlineAt()).isAfter(LocalDateTime.now());
    }

    // ---------- 3. 水质异常 → 暂停售水 + 通知物业 ----------

    @Test
    @Order(3)
    void abnormalWaterQualityPausesDeviceAndNotifiesProperty() throws Exception {
        sendTelemetry("DEV-001", 120, 150, 0.5);         // TDS 超标
        await("DEV-001 水质异常预警", () -> hasAlert("DEV-001", AlertType.WATER_QUALITY_ABNORMAL));
        await("DEV-001 暂停售水", () ->
                device("DEV-001").getStatus() == DeviceStatus.PAUSED);

        Device device = device("DEV-001");
        assertThat(device.getPauseReason()).contains("水质异常");

        await("物业收到通知", () -> notificationRepository
                .findByTargetTypeAndTargetRefOrderByCreatedAtDesc("PROPERTY", "阳光花园/3栋")
                .stream().anyMatch(n -> n.getType().equals("WATER_QUALITY_ABNORMAL")));

        // 水质异常自动生成复检工单
        assertThat(workOrderRepository.findByDeviceOrderByCreatedAtDesc(device).stream()
                .anyMatch(o -> o.getType() == WorkOrderType.RECHECK)).isTrue();
    }

    // ---------- 4. 取水扣费：成功 / 停售拒绝 / 余额不足 ----------

    @Test
    @Order(4)
    void intakeChargingAndRejections() {
        // 停售设备拒绝取水并说明原因
        assertThatThrownBy(() -> intakeService.intake("DEV-001", "ACC-1001", 5, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂停售水");

        // 正常设备取水成功并扣费
        WaterIntake intake = intakeService.intake("DEV-002", "ACC-1001", 10, false);
        assertThat(intake.getFee()).isEqualByComparingTo("3.00");
        ResidentAccount acc = accountRepository.findByAccountNo("ACC-1001").orElseThrow();
        assertThat(acc.getBalance()).isEqualByComparingTo("197.00");
        assertThat(chargeRepository.findByAccountOrderByCreatedAtDesc(acc).stream()
                .anyMatch(c -> c.getStatus() == ChargeStatus.SUCCESS
                        && c.getType() == ChargeType.WATER_FEE)).isTrue();

        // 余额不足 → 扣费失败记录 + 拒绝
        assertThatThrownBy(() -> intakeService.intake("DEV-002", "ACC-1003", 5, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额不足");
        ResidentAccount poor = accountRepository.findByAccountNo("ACC-1003").orElseThrow();
        assertThat(chargeRepository.findByAccountOrderByCreatedAtDesc(poor).stream()
                .anyMatch(c -> c.getStatus() == ChargeStatus.FAILED)).isTrue();
        assertThat(hasAlert("DEV-002", AlertType.CHARGE_FAILED)).isTrue();
    }

    // ---------- 5. 投诉聚集 → 暂停售水 ----------

    @Test
    @Order(5)
    void complaintClusterPausesDevice() {
        Device device = device("DEV-002");
        ResidentAccount acc = accountRepository.findByAccountNo("ACC-1002").orElseThrow();
        intakeService.fileComplaint(device, acc, null, "ODOR", "水有异味");
        intakeService.fileComplaint(device, acc, null, "ODOR", "水有异味");
        assertThat(device("DEV-002").getStatus()).isEqualTo(DeviceStatus.NORMAL);

        intakeService.fileComplaint(device, acc, null, "ODOR", "水有异味");
        awaitSafe(() -> device("DEV-002").getStatus() == DeviceStatus.PAUSED);
        assertThat(device("DEV-002").getPauseReason()).contains("投诉");
        assertThat(hasAlert("DEV-002", AlertType.COMPLAINT_CLUSTER)).isTrue();
    }

    private static void awaitSafe(Supplier<Boolean> cond) {
        try {
            await("状态变更", cond);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- 6. 换芯履约全流程 ----------

    @Test
    @Order(6)
    void replacementFulfillmentFlow() throws Exception {
        // DEV-003 初始寿命 12%，遥测触发预警与换芯工单
        sendTelemetry("DEV-003", 0, 40, 0.5);
        await("DEV-003 基线遥测", () -> telemetryCount("DEV-003") >= 1);
        sendTelemetry("DEV-003", 100, 40, 0.5);
        await("DEV-003 换芯工单", () -> workOrderRepository
                .findByDeviceOrderByCreatedAtDesc(device("DEV-003")).stream()
                .anyMatch(o -> o.getType() == WorkOrderType.FILTER_REPLACEMENT));

        WorkOrder order = workOrderRepository.findByDeviceOrderByCreatedAtDesc(device("DEV-003"))
                .stream().filter(o -> o.getType() == WorkOrderType.FILTER_REPLACEMENT)
                .findFirst().orElseThrow();
        String techNo = order.getTechnician().getTechNo();

        // 非派单师傅不能确认到场
        String otherTech = techNo.equals("T001") ? "T002" : "T001";
        assertThatThrownBy(() -> workOrderService.arrive(order.getOrderNo(), otherTech))
                .isInstanceOf(BusinessException.class);

        // 未到场不能完成换芯
        assertThatThrownBy(() -> replacementService.completeReplacement(order.getOrderNo(), techNo,
                "FLT-003-A", "FLT-003-B", "BATCH-2026-10", "http://img/1.jpg", 15, 40.0, 0.5))
                .isInstanceOf(BusinessException.class);

        workOrderService.arrive(order.getOrderNo(), techNo);

        // 旧滤芯编号不符 → 拒绝（防止假换芯）
        assertThatThrownBy(() -> replacementService.completeReplacement(order.getOrderNo(), techNo,
                "FLT-WRONG", "FLT-003-B", "BATCH-2026-10", "http://img/1.jpg", 15, 40.0, 0.5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");

        // 冲洗时间不足 → 拒绝
        assertThatThrownBy(() -> replacementService.completeReplacement(order.getOrderNo(), techNo,
                "FLT-003-A", "FLT-003-B", "BATCH-2026-10", "http://img/1.jpg", 5, 40.0, 0.5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("冲洗");

        // 正确履约：旧滤芯扫码一致、照片、冲洗 15 分钟、复检合格
        FilterReplacement replacement = replacementService.completeReplacement(order.getOrderNo(),
                techNo, "FLT-003-A", "FLT-003-B", "BATCH-2026-10",
                "http://img/1.jpg,http://img/2.jpg", 15, 40.0, 0.5);
        assertThat(replacement.getRecheckResult()).isEqualTo(RecheckResult.PASS);

        // 设备恢复售水，新滤芯启用，旧滤芯退役
        assertThat(device("DEV-003").getStatus()).isEqualTo(DeviceStatus.NORMAL);
        assertThat(activeFilter("DEV-003").getFilterNo()).isEqualTo("FLT-003-B");
        assertThat(activeFilter("DEV-003").getLifePercent()).isEqualTo(100);
        assertThat(filterRepository.findByFilterNo("FLT-003-A").orElseThrow().getStatus())
                .isEqualTo(FilterStatus.REPLACED);

        // 复检记录留痕
        assertThat(recheckRepository.findByDeviceOrderByCreatedAtDesc(device("DEV-003")).stream()
                .anyMatch(r -> r.getKind().equals("REPLACEMENT") && r.getResult() == RecheckResult.PASS))
                .isTrue();

        // 维护费按楼栋分摊：滨江新城 1 栋 2 户，各 60 元
        ResidentAccount acc4 = accountRepository.findByAccountNo("ACC-1004").orElseThrow();
        ResidentAccount acc5 = accountRepository.findByAccountNo("ACC-1005").orElseThrow();
        assertThat(acc4.getBalance()).isEqualByComparingTo("240.00");  // 300 - 60
        assertThat(acc5.getBalance()).isEqualByComparingTo("20.00");   // 80 - 60
        assertThat(chargeRepository.findByAccountOrderByCreatedAtDesc(acc4).stream()
                .anyMatch(c -> c.getType() == ChargeType.MAINTENANCE_SHARE
                        && c.getStatus() == ChargeStatus.SUCCESS
                        && c.getAmount().compareTo(new java.math.BigDecimal("60.00")) == 0)).isTrue();

        // 预警解除
        assertThat(alertRepository.findByDeviceOrderByCreatedAtDesc(device("DEV-003")).stream()
                .filter(a -> a.getType() == AlertType.FILTER_LIFE_LOW)
                .allMatch(a -> a.getStatus() == AlertStatus.RESOLVED)).isTrue();
    }

    // ---------- 7. 换芯后仍有异味 → 免费复检工单 ----------

    @Test
    @Order(7)
    void postReplacementOdorTriggersFreeRecheck() {
        Device device = device("DEV-003");
        ResidentAccount acc = accountRepository.findByAccountNo("ACC-1004").orElseThrow();
        intakeService.fileComplaint(device, acc, null, "ODOR", "换芯后仍有异味");

        assertThat(hasAlert("DEV-003", AlertType.POST_REPLACEMENT_ODOR)).isTrue();
        WorkOrder recheck = workOrderRepository.findByDeviceOrderByCreatedAtDesc(device).stream()
                .filter(o -> o.getType() == WorkOrderType.RECHECK).findFirst().orElseThrow();
        assertThat(recheck.isFreeOfCharge()).isTrue();
    }

    // ---------- 8. 师傅未按时到场 → 预警 + 改派 ----------

    @Test
    @Order(8)
    void technicianNoShowEscalates() {
        Device device = device("DEV-003");
        WorkOrder order = workOrderRepository.findByDeviceOrderByCreatedAtDesc(device).stream()
                .filter(o -> o.getStatus() == WorkOrderStatus.ASSIGNED).findFirst().orElseThrow();
        String originalTech = order.getTechnician().getTechNo();

        // 强制超时
        order.setDeadlineAt(LocalDateTime.now().minusHours(1));
        workOrderRepository.save(order);

        int escalated = workOrderService.escalateOverdueOrders();
        assertThat(escalated).isGreaterThanOrEqualTo(1);

        WorkOrder updated = workOrderRepository.findByOrderNo(order.getOrderNo()).orElseThrow();
        assertThat(updated.getTechnician().getTechNo()).isNotEqualTo(originalTech);
        assertThat(hasAlert("DEV-003", AlertType.TECHNICIAN_NO_SHOW)).isTrue();
    }

    // ---------- 9. 发票开具与抬头错误更正 ----------

    @Test
    @Order(9)
    void invoiceIssueAndCorrectTitle() {
        ResidentAccount acc = accountRepository.findByAccountNo("ACC-1001").orElseThrow();
        ChargeRecord charge = chargeRepository.findByAccountOrderByCreatedAtDesc(acc).stream()
                .filter(c -> c.getStatus() == ChargeStatus.SUCCESS).findFirst().orElseThrow();

        Invoice invoice = invoiceService.issue(charge.getChargeNo(), "阳光花园物业（错误抬头）", "91310000XXXX");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);

        Invoice reissued = invoiceService.correctTitle(invoice.getInvoiceNo(),
                "上海阳光花园物业管理有限公司", "91310000YYYY");
        assertThat(reissued.getStatus()).isEqualTo(InvoiceStatus.REISSUED);
        assertThat(reissued.getReissuedFrom()).isEqualTo(invoice.getInvoiceNo());
        assertThat(invoiceService.get(invoice.getInvoiceNo()).getStatus())
                .isEqualTo(InvoiceStatus.TITLE_ERROR);

        // 已作废发票不能再次更正
        assertThatThrownBy(() -> invoiceService.correctTitle(invoice.getInvoiceNo(), "x", "y"))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 10. 履约链路 + 运营策略 ----------

    @Test
    @Order(10)
    void fulfillmentChainAndOperationsStrategy() {
        List<String> eventTypes = eventRepository.findByDeviceOrderByCreatedAtAsc(device("DEV-003"))
                .stream().map(DeviceEvent::getEventType).distinct().toList();
        assertThat(eventTypes).contains("TELEMETRY", "ALERT", "ORDER_CREATED", "ORDER_ARRIVED",
                "REPLACEMENT", "RECHECK", "CHARGE", "DEVICE_RESUMED", "COMPLAINT");

        // 运营端换芯策略覆盖全部设备且含建议
        List<OperationsService.DeviceStrategy> strategies = operationsService.replacementStrategy();
        assertThat(strategies).hasSize(3);
        assertThat(strategies).allSatisfy(s -> assertThat(s.recommendedAction()).isNotBlank());
        // DEV-001 水质异常停售中 → 建议优先恢复
        assertThat(strategies.stream().filter(s -> s.deviceNo().equals("DEV-001"))
                .findFirst().orElseThrow().deviceStatus()).isEqualTo("PAUSED");
    }
}
