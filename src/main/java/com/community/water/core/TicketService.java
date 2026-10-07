package com.community.water.core;

import com.community.water.config.AppProperties;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;
import static com.community.water.domain.TicketStatus.*;

/**
 * 报修工单：寿命预警/水质异常/投诉聚集/换芯异味/监管抽查统一报修派单；
 * 记录 SLA 到场时限，超时自动升级"师傅未按时到场"。
 */
@Service
public class TicketService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<TicketStatus> OPEN_STATES = List.of(OPEN, ASSIGNED, TECHNICIAN_LATE, IN_PROGRESS, AWAITING_RETEST);

    private final MaintenanceTicketRepository ticketRepository;
    private final TechnicianRepository technicianRepository;
    private final AlertService alertService;
    private final EventBus eventBus;
    private final NotificationService notificationService;
    private final AppProperties props;

    public TicketService(MaintenanceTicketRepository ticketRepository,
                         TechnicianRepository technicianRepository,
                         AlertService alertService, EventBus eventBus,
                         NotificationService notificationService, AppProperties props) {
        this.ticketRepository = ticketRepository;
        this.technicianRepository = technicianRepository;
        this.alertService = alertService;
        this.eventBus = eventBus;
        this.notificationService = notificationService;
        this.props = props;
    }

    /** 创建报修工单；同设备同触发原因存在未结工单时直接复用，避免重复派单。 */
    @Transactional
    public MaintenanceTicket openTicket(Device device, TicketTrigger trigger, String detail, FulfillmentCase c) {
        MaintenanceTicket existing = ticketRepository.findByDeviceIdOrderByCreatedAtDesc(device.getId()).stream()
                .filter(t -> OPEN_STATES.contains(t.getStatus()))
                .findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        MaintenanceTicket t = new MaintenanceTicket();
        t.setTicketNo("WO-" + OffsetDateTime.now().format(NO_FMT) + "-" + device.getDeviceCode()
                + "-" + ThreadLocalRandom.current().nextInt(100, 999));
        t.setDeviceId(device.getId());
        t.setDeviceCode(device.getDeviceCode());
        t.setCommunityId(device.getCommunityId());
        t.setBuilding(device.getBuilding());
        t.setTrigger(trigger);
        t.setTriggerDetail(detail);
        t.setStatus(OPEN);
        t.setCaseId(c == null ? null : c.getId());
        ticketRepository.save(t);
        if (c != null) {
            c.setSummary(c.getSummary() + " | 已生成报修单 " + t.getTicketNo());
        }
        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.TICKET_CREATED, t.getTicketNo(),
                EventBus.payload("ticketNo", t.getTicketNo(), "deviceCode", device.getDeviceCode(),
                        "trigger", trigger.name(), "detail", detail,
                        "caseNo", c == null ? null : c.getCaseNo()));
        return t;
    }

    @Transactional
    public MaintenanceTicket assign(String ticketNo, Long technicianId) {
        MaintenanceTicket t = require(ticketNo);
        if (t.getStatus() != OPEN && t.getStatus() != TECHNICIAN_LATE) {
            throw ApiException.conflict("工单当前状态 " + t.getStatus() + " 不允许派单");
        }
        Technician tech = technicianRepository.findById(technicianId)
                .orElseThrow(() -> ApiException.badRequest("师傅不存在: id=" + technicianId));
        t.setTechnicianId(tech.getId());
        t.setTechnicianName(tech.getName());
        t.setAssignedAt(OffsetDateTime.now());
        t.setDueAt(t.getAssignedAt().plusHours(props.arrivalSlaHours()));
        t.setStatus(ASSIGNED);
        t.setUpdatedAt(OffsetDateTime.now());
        ticketRepository.save(t);

        Device device = new Device();
        device.setId(t.getDeviceId());
        device.setDeviceCode(t.getDeviceCode());
        device.setCommunityId(t.getCommunityId());
        device.setBuilding(t.getBuilding());
        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.TICKET_ASSIGNED, t.getTicketNo(),
                EventBus.payload("ticketNo", t.getTicketNo(), "technician", tech.getName(),
                        "dueAt", t.getDueAt().toString()));
        notificationService.notifyTechnician(device, tech.getName(),
                "新换芯工单 " + t.getTicketNo(),
                "请于 " + t.getDueAt() + " 前到 " + t.getBuilding() + " 处理：" + t.getTrigger(),
                null, t);
        return t;
    }

    @Transactional
    public MaintenanceTicket arrive(String ticketNo, String note) {
        MaintenanceTicket t = require(ticketNo);
        if (t.getStatus() != ASSIGNED && t.getStatus() != TECHNICIAN_LATE) {
            throw ApiException.conflict("工单当前状态 " + t.getStatus() + " 不允许到场确认");
        }
        t.setArrivedAt(OffsetDateTime.now());
        t.setStatus(IN_PROGRESS);
        t.setUpdatedAt(OffsetDateTime.now());
        return ticketRepository.save(t);
    }

    @Transactional(readOnly = true)
    public MaintenanceTicket require(String ticketNo) {
        return ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> ApiException.notFound("工单不存在: " + ticketNo));
    }

    /** 定时扫描超时未到场工单，升级为师傅迟到并告警物业。 */
    @Transactional
    public int scanOverdue() {
        List<MaintenanceTicket> overdue = ticketRepository.findOverdue(List.of(ASSIGNED), OffsetDateTime.now());
        for (MaintenanceTicket t : overdue) {
            t.setStatus(TECHNICIAN_LATE);
            t.setLateAlerted(true);
            t.setUpdatedAt(OffsetDateTime.now());
            ticketRepository.save(t);

            Device device = new Device();
            device.setId(t.getDeviceId());
            device.setDeviceCode(t.getDeviceCode());
            device.setCommunityId(t.getCommunityId());
            device.setBuilding(t.getBuilding());
            Alert alert = alertService.open(device, AlertType.TECHNICIAN_LATE,
                    "工单 " + t.getTicketNo() + " 已超过要求到场时间 " + t.getDueAt(),
                    null, t, null, null);
            notificationService.notifyProperty(device, "【师傅迟到】" + t.getTicketNo(),
                    "派单师傅未在 " + t.getDueAt() + " 前到场，已升级催办。", null, t);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.TECHNICIAN_LATE, t.getTicketNo(),
                    EventBus.payload("ticketNo", t.getTicketNo(), "alertId", alert.getId(),
                            "dueAt", t.getDueAt().toString()));
        }
        return overdue.size();
    }

    /** 为测试/补录提供直接置派单时间的入口（验证迟到逻辑而无需等待 24h）。 */
    @Transactional
    public MaintenanceTicket backdateDue(String ticketNo, OffsetDateTime assignedAt, OffsetDateTime dueAt) {
        MaintenanceTicket t = require(ticketNo);
        t.setAssignedAt(assignedAt);
        t.setDueAt(dueAt);
        return ticketRepository.save(t);
    }
}
