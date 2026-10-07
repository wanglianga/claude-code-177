package com.community.water.web;

import com.community.water.core.*;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.support.CurrentUser;
import com.community.water.support.CurrentUsers;
import com.community.water.web.dto.CaseView;
import com.community.water.web.dto.Requests.ComplaintRequest;
import com.community.water.web.dto.Requests.MaintenanceShareRequest;
import com.community.water.web.dto.Requests.ManualTicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 物业端：关注居民体验与费用解释。查看本小区履约链路、投诉、通知，
 * 代登记投诉、人工报修、按楼栋分摊维护费。
 */
@RestController
@RequestMapping("/api/property")
@Tag(name = "物业端", description = "居民体验、费用解释、楼栋分摊（X-Role=PROPERTY，X-Community-Id=小区ID）")
public class PropertyController {

    private final CaseQueryService caseQueryService;
    private final ComplaintService complaintService;
    private final ChargeService chargeService;
    private final DeviceService deviceService;
    private final CaseService caseService;
    private final TicketService ticketService;
    private final ExplanationService explanationService;
    private final DeviceRepository deviceRepository;
    private final ComplaintRepository complaintRepository;
    private final ChargeRepository chargeRepository;
    private final NotificationRepository notificationRepository;
    private final AlertRepository alertRepository;

    public PropertyController(CaseQueryService caseQueryService, ComplaintService complaintService,
                              ChargeService chargeService, DeviceService deviceService,
                              CaseService caseService, TicketService ticketService,
                              ExplanationService explanationService, DeviceRepository deviceRepository,
                              ComplaintRepository complaintRepository, ChargeRepository chargeRepository,
                              NotificationRepository notificationRepository, AlertRepository alertRepository) {
        this.caseQueryService = caseQueryService;
        this.complaintService = complaintService;
        this.chargeService = chargeService;
        this.deviceService = deviceService;
        this.caseService = caseService;
        this.ticketService = ticketService;
        this.explanationService = explanationService;
        this.deviceRepository = deviceRepository;
        this.complaintRepository = complaintRepository;
        this.chargeRepository = chargeRepository;
        this.notificationRepository = notificationRepository;
        this.alertRepository = alertRepository;
    }

    private Long communityId() {
        CurrentUser u = CurrentUsers.require();
        if (u.communityId() == null) {
            throw new ApiException(400, "物业请求需携带 X-Community-Id");
        }
        return u.communityId();
    }

    @GetMapping("/dashboard")
    @Operation(summary = "小区看板：设备状态、未结预警、履约链路概况")
    public Map<String, Object> dashboard() {
        Long cid = communityId();
        List<Device> devices = deviceRepository.findByCommunityId(cid);
        return Map.of(
                "communityId", cid,
                "deviceTotal", devices.size(),
                "suspended", devices.stream().filter(d -> d.getStatus() == DeviceStatus.SUSPENDED).count(),
                "maintenance", devices.stream().filter(d -> d.getStatus() == DeviceStatus.MAINTENANCE).count(),
                "active", devices.stream().filter(d -> d.getStatus() == DeviceStatus.ACTIVE).count(),
                "openAlerts", alertRepository.findByCommunityIdAndStatusOrderByCreatedAtDesc(cid, AlertStatus.OPEN),
                "openCases", caseService.listByCommunity(cid).stream().filter(c -> !"CLOSED".equals(c.getStage())).toList());
    }

    @GetMapping("/devices")
    @Operation(summary = "本小区设备列表")
    public List<Device> devices() {
        return deviceRepository.findByCommunityId(communityId());
    }

    @GetMapping("/devices/{deviceCode}/explain")
    @Operation(summary = "对居民的解释口径（暂停原因/恢复条件/收费方式）")
    public Map<String, Object> explain(@PathVariable String deviceCode) {
        return explanationService.explainDevice(deviceCode);
    }

    @GetMapping("/cases")
    @Operation(summary = "本小区全部设备履约链路")
    public List<CaseView> cases() {
        return caseQueryService.byCommunity(communityId());
    }

    @GetMapping("/cases/{caseNo}")
    @Operation(summary = "履约链路详情：设备-滤芯-取水-投诉-报修-换芯-复检-收费-发票全留痕")
    public CaseView caseDetail(@PathVariable String caseNo) {
        CaseView v = caseQueryService.byCaseNo(caseNo);
        if (!v.communityId().equals(communityId())) {
            throw new ApiException(403, "无权查看其他小区链路");
        }
        return v;
    }

    @PostMapping("/complaints")
    @Operation(summary = "代居民登记异味投诉")
    public Map<String, Object> addComplaint(@Valid @RequestBody ComplaintRequest req) {
        var c = complaintService.registerByProperty(req);
        return Map.of("complaintId", c.getId(), "status", c.getStatus().name());
    }

    @GetMapping("/complaints")
    @Operation(summary = "本小区投诉列表")
    public List<Complaint> complaints() {
        return complaintRepository.findAll().stream()
                .filter(c -> c.getCommunityId().equals(communityId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    @PostMapping("/tickets/manual")
    @Operation(summary = "人工报修（如现场巡查发现问题）")
    public MaintenanceTicket manualTicket(@Valid @RequestBody ManualTicketRequest req) {
        Device device = deviceService.requireByCode(req.deviceCode());
        if (!device.getCommunityId().equals(communityId())) {
            throw new ApiException(403, "设备不属于本小区");
        }
        FulfillmentCase c = caseService.obtainOpenCase(device, TicketTrigger.MANUAL.name(), req.reason());
        return ticketService.openTicket(device, TicketTrigger.MANUAL, req.reason(), c);
    }

    @PostMapping("/maintenance-share")
    @Operation(summary = "按楼栋分摊换芯维护费到各住户账户并逐户扣费开票")
    public Map<String, Object> share(@Valid @RequestBody MaintenanceShareRequest req) {
        Device device = deviceService.requireByCode(req.deviceCode());
        if (!device.getCommunityId().equals(communityId())) {
            throw new ApiException(403, "设备不属于本小区");
        }
        FulfillmentCase c = caseService.findOpenCase(device.getId());
        if (c == null) {
            c = caseService.findLatestCase(device.getId());
            if (c != null) {
                caseService.reopenIfClosed(c, "楼栋维护费分摊需对账");
            } else {
                c = caseService.createCase(device, "MAINTENANCE_SHARE",
                        "楼栋维护费分摊：" + (req.reason() == null ? "换芯费用" : req.reason()));
            }
        }
        List<Charge> charges = chargeService.shareByBuilding(device, req.totalAmount(), req.reason(), c);
        return Map.of(
                "households", charges.size(),
                "succeeded", charges.stream().filter(ch -> ch.getStatus() == ChargeStatus.SUCCESS).count(),
                "failed", charges.stream().filter(ch -> ch.getStatus() == ChargeStatus.FAILED).count(),
                "charges", charges,
                "caseNo", c == null ? null : c.getCaseNo());
    }

    @GetMapping("/charges")
    @Operation(summary = "本小区收费记录（取水费/楼栋分摊）")
    public List<Charge> charges() {
        return chargeRepository.findByCommunityIdOrderByCreatedAtDesc(communityId());
    }

    @GetMapping("/notifications")
    @Operation(summary = "本小区通知（停机/恢复/迟到等）")
    public List<Notification> notifications() {
        return notificationRepository.findByCommunityIdOrderByCreatedAtDesc(communityId());
    }
}
