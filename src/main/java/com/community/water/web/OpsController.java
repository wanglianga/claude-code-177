package com.community.water.web;

import com.community.water.core.*;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.CaseView;
import com.community.water.web.dto.Requests.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 运营端：按设备健康与投诉决定换芯策略。接收设备遥测、派单、监管抽查、
 * 发票抬头更正、失败扣费重试、查看全部设备履约链路与预警。
 */
@RestController
@RequestMapping("/api/ops")
@Tag(name = "运营端", description = "遥测、策略、派单、监管抽查、全局履约链路（X-Role=OPERATOR）")
public class OpsController {

    private final TelemetryService telemetryService;
    private final DeviceHealthService deviceHealthService;
    private final SetupService setupService;
    private final TicketService ticketService;
    private final MaintenanceExecutionService executionService;
    private final InvoiceService invoiceService;
    private final ChargeService chargeService;
    private final CaseQueryService caseQueryService;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final TechnicianRepository technicianRepository;
    public OpsController(TelemetryService telemetryService,
                         DeviceHealthService deviceHealthService,
                         SetupService setupService,
                         TicketService ticketService,
                         MaintenanceExecutionService executionService,
                         InvoiceService invoiceService,
                         ChargeService chargeService,
                         CaseQueryService caseQueryService,
                         DeviceRepository deviceRepository,
                         AlertRepository alertRepository,
                         TechnicianRepository technicianRepository) {
        this.telemetryService = telemetryService;
        this.deviceHealthService = deviceHealthService;
        this.setupService = setupService;
        this.ticketService = ticketService;
        this.executionService = executionService;
        this.invoiceService = invoiceService;
        this.chargeService = chargeService;
        this.caseQueryService = caseQueryService;
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
        this.technicianRepository = technicianRepository;
    }

    // ---------- 遥测 ----------

    @PostMapping("/telemetry")
    @Operation(summary = "接收设备遥测（寿命/出水量/TDS/余氯/流量/故障码/最近维护），触发策略评估")
    public Map<String, Object> telemetry(@Valid @RequestBody TelemetryRequest req) {
        TelemetryRecord t = telemetryService.ingest(req);
        return Map.of("telemetryId", t.getId(), "eventTime", t.getEventTime().toString());
    }

    // ---------- 设备健康与换芯策略 ----------

    @GetMapping("/health")
    @Operation(summary = "全部设备健康画像与动态换芯建议")
    public List<Map<String, Object>> health() {
        return deviceHealthService.allHealth();
    }

    @GetMapping("/health/{deviceCode}")
    @Operation(summary = "单台设备健康画像（寿命因子、投诉、维护频率、紧迫度）")
    public Map<String, Object> healthOne(@PathVariable String deviceCode) {
        return deviceHealthService.healthOf(deviceCode);
    }

    @PutMapping("/devices/{deviceCode}/strategy")
    @Operation(summary = "按设备健康/投诉动态调整寿命阈值、水质限值、水价（换芯策略不是固定日期）")
    public Device updateStrategy(@PathVariable String deviceCode, @Valid @RequestBody DeviceStrategyRequest req) {
        return setupService.updateStrategy(deviceCode, req);
    }

    // ---------- 档案管理 ----------

    @GetMapping("/devices")
    @Operation(summary = "全部设备")
    public List<Device> devices() {
        return deviceRepository.findAll();
    }

    @PostMapping("/communities")
    @Operation(summary = "新建小区")
    public Community createCommunity(@RequestBody Map<String, String> body) {
        return setupService.createCommunity(body.get("name"), body.get("address"));
    }

    @PostMapping("/devices")
    @Operation(summary = "新建设备")
    public Device createDevice(@Valid @RequestBody CreateDeviceRequest req) {
        return setupService.createDevice(req);
    }

    @PostMapping("/devices/{deviceCode}/filters")
    @Operation(summary = "为设备安装/登记在用滤芯")
    public WaterFilter installFilter(@PathVariable String deviceCode, @Valid @RequestBody CreateFilterRequest req) {
        Device d = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceCode));
        return setupService.installInitialFilter(d.getId(), req);
    }

    @PostMapping("/technicians")
    @Operation(summary = "新建维护师傅")
    public Technician createTechnician(@RequestBody Map<String, String> body) {
        Long cid = body.get("communityId") == null ? null : Long.parseLong(body.get("communityId"));
        return setupService.createTechnician(body.get("name"), body.get("phone"), cid);
    }

    @GetMapping("/technicians")
    @Operation(summary = "师傅列表")
    public List<Technician> technicians() {
        return technicianRepository.findAll();
    }

    @PostMapping("/accounts")
    @Operation(summary = "新建居民账户")
    public ResidentAccount createAccount(@RequestBody Map<String, String> body) {
        return setupService.createAccount(body.get("accountNo"), body.get("residentName"),
                Long.parseLong(body.get("communityId")), body.get("building"), body.get("phone"),
                body.get("balance") == null ? BigDecimal.ZERO : new BigDecimal(body.get("balance")));
    }

    // ---------- 派单 / 迟到 ----------

    @PostMapping("/tickets/{ticketNo}/assign")
    @Operation(summary = "为报修单安排维护师傅（寿命预警生成后派单）")
    public MaintenanceTicket assign(@PathVariable String ticketNo, @Valid @RequestBody AssignRequest req) {
        return ticketService.assign(ticketNo, req.technicianId());
    }

    @PostMapping("/tickets/scan-late")
    @Operation(summary = "立即扫描超过到场 SLA 的工单（演示师傅未按时到场）")
    public Map<String, Object> scanLate() {
        return Map.of("lateCount", ticketService.scanOverdue());
    }

    // ---------- 监管抽查 ----------

    @PostMapping("/regulatory-audits")
    @Operation(summary = "监管抽查水质记录；不合格立即停机并纳入履约链路留痕")
    public WaterQualityRetest audit(@Valid @RequestBody RegulatoryAuditRequest req) {
        return executionService.regulatoryAudit(req);
    }

    // ---------- 收费与发票 ----------

    @PostMapping("/charges/{chargeNo}/retry")
    @Operation(summary = "重试失败扣费（居民账户扣费失败后的补扣）")
    public Charge retryCharge(@PathVariable String chargeNo) {
        return chargeService.retry(chargeNo);
    }

    @PostMapping("/invoices/correct-title")
    @Operation(summary = "发票抬头错误：冲红原发票并重开正确发票，保留更正轨迹")
    public Invoice correctTitle(@Valid @RequestBody CorrectInvoiceTitleRequest req) {
        return invoiceService.correctTitle(req.invoiceNo(), req.newTitle(), req.newTaxNo());
    }

    // ---------- 预警与履约链路 ----------

    @GetMapping("/alerts")
    @Operation(summary = "全部未处理预警（可按 status=OPEN/HANDLED 过滤）")
    public List<Alert> alerts(@RequestParam(defaultValue = "OPEN") String status) {
        return "ALL".equalsIgnoreCase(status) ? alertRepository.findAll()
                : alertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.valueOf(status));
    }

    @GetMapping("/cases")
    @Operation(summary = "全部设备履约链路（设备-滤芯-取水-报修-换芯-复检-收费-发票）")
    public List<CaseView> cases() {
        return caseQueryService.all();
    }

    @GetMapping("/cases/{caseNo}")
    @Operation(summary = "履约链路详情与时间线")
    public CaseView caseDetail(@PathVariable String caseNo) {
        return caseQueryService.byCaseNo(caseNo);
    }

    @GetMapping("/devices/{deviceCode}/cases/latest")
    @Operation(summary = "设备最近一条履约链路（确认每次换芯是否真实完成）")
    public CaseView latestCase(@PathVariable String deviceCode) {
        return caseQueryService.latestByDevice(deviceCode);
    }
}
