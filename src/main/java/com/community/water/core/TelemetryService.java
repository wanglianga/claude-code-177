package com.community.water.core;

import com.community.water.config.AppProperties;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.Requests.TelemetryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;

/**
 * 遥测处理：净水机持续上报寿命、出水量、TDS、余氯、流量、故障码、最近维护时间。
 * 在此完成：累计出水量、动态换芯策略评估、寿命预警/提前耗尽、水质异常停机报修。
 */
@Service
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final WaterFilterRepository filterRepository;
    private final ComplaintRepository complaintRepository;
    private final FilterReplacementRepository replacementRepository;
    private final FilterStrategyService strategyService;
    private final CaseService caseService;
    private final DeviceService deviceService;
    private final TicketService ticketService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final EventBus eventBus;
    private final AppProperties props;

    public TelemetryService(TelemetryRepository telemetryRepository,
                            DeviceRepository deviceRepository,
                            WaterFilterRepository filterRepository,
                            ComplaintRepository complaintRepository,
                            FilterReplacementRepository replacementRepository,
                            FilterStrategyService strategyService,
                            CaseService caseService,
                            DeviceService deviceService,
                            TicketService ticketService,
                            AlertService alertService,
                            NotificationService notificationService,
                            EventBus eventBus,
                            AppProperties props) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
        this.filterRepository = filterRepository;
        this.complaintRepository = complaintRepository;
        this.replacementRepository = replacementRepository;
        this.strategyService = strategyService;
        this.caseService = caseService;
        this.deviceService = deviceService;
        this.ticketService = ticketService;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.eventBus = eventBus;
        this.props = props;
    }

    @Transactional
    public TelemetryRecord ingest(TelemetryRequest req) {
        Device device = deviceRepository.findByDeviceCode(req.deviceCode())
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + req.deviceCode()));
        OffsetDateTime now = OffsetDateTime.now();

        TelemetryRecord t = new TelemetryRecord();
        t.setDeviceId(device.getId());
        t.setDeviceCode(device.getDeviceCode());
        t.setEventTime(req.eventTime() == null ? now : req.eventTime());
        t.setFilterLifePercent(req.filterLifePercent());
        t.setWaterOutputLiters(req.waterOutputLiters());
        t.setTds(req.tds());
        t.setChlorine(req.chlorine());
        t.setFlowRate(req.flowRate());
        t.setFaultCode(req.faultCode());
        t.setLastMaintenanceAt(req.lastMaintenanceAt());
        telemetryRepository.save(t);

        // 累计出水量（寿命动态因子，由设备遥测统一上报）
        WaterFilter filter = filterRepository
                .findFirstByDeviceIdAndStatusOrderByInstalledAtDesc(device.getId(), FilterStatus.IN_USE)
                .orElse(null);
        if (filter != null) {
            filter.setUsedLiters(filter.getUsedLiters() + req.waterOutputLiters());
            filter.setLifePercent(req.filterLifePercent());
            filterRepository.save(filter);
        }
        device.setCumulativeOutputLiters(device.getCumulativeOutputLiters() + req.waterOutputLiters());
        deviceRepository.save(device);

        // 遥测确认事件发往设备事件 topic（不能回发 telemetry.v1，否则消费者自激循环）
        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.TELEMETRY_RECEIVED, device.getDeviceCode(),
                EventBus.payload("deviceCode", device.getDeviceCode(), "life", req.filterLifePercent(),
                        "tds", req.tds(), "chlorine", req.chlorine(), "faultCode", req.faultCode(),
                        "outputLiters", req.waterOutputLiters()));

        evaluate(device, filter, req, now);
        return t;
    }

    private void evaluate(Device device, WaterFilter filter, TelemetryRequest req, OffsetDateTime now) {
        boolean fault = req.faultCode() != null && !req.faultCode().isBlank() && !"0".equals(req.faultCode());
        boolean waterHealthy = req.tds() <= device.getTdsLimit()
                && req.chlorine() <= device.getChlorineLimit()
                && !fault;

        long recentComplaints = complaintRepository.countByDeviceIdAndStatusAndCreatedAtAfter(
                device.getId(), ComplaintStatus.OPEN, now.minusHours(props.complaintWindowHours()));
        OffsetDateTime since30 = now.minusDays(30);
        int recentReplacements = (int) replacementRepository.findByDeviceIdOrderByReplacedAtDesc(device.getId())
                .stream().filter(r -> r.getReplacedAt() != null && !r.getReplacedAt().isBefore(since30)).count();

        FilterStrategyService.StrategyDecision d = strategyService.evaluate(
                device, req.filterLifePercent(), filter, recentComplaints, recentReplacements, waterHealthy);

        // 1) 水质异常：公共饮水安全优先，立即停机、通知物业、报修
        if (!waterHealthy) {
            String reason = String.format("水质异常：TDS=%.1f（限值 %d），余氯=%.2f（限值 %.1f）%s",
                    req.tds(), device.getTdsLimit(), req.chlorine(), device.getChlorineLimit(),
                    fault ? "，故障码 " + req.faultCode() : "");
            FulfillmentCase c = caseService.obtainOpenCase(device, TicketTrigger.WATER_QUALITY.name(), reason);
            alertService.open(device, AlertType.WATER_QUALITY, reason, c, null, null, null);
            deviceService.suspend(device, reason, c);
            MaintenanceTicket ticket = ticketService.openTicket(device, TicketTrigger.WATER_QUALITY, reason, c);
            notificationService.notifyProperty(device, "【水质异常已停机】" + device.getDeviceCode(),
                    reason + "。工单 " + ticket.getTicketNo() + "，复检合格前不会恢复售水。", c, ticket);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.WATER_QUALITY_ABNORMAL, device.getDeviceCode(),
                    EventBus.payload("deviceCode", device.getDeviceCode(), "tds", req.tds(),
                            "chlorine", req.chlorine(), "faultCode", req.faultCode(),
                            "caseNo", c.getCaseNo(), "ticketNo", ticket.getTicketNo()));
        }

        // 2) 寿命提前耗尽：标称容量/上报都应富余，却因水质、投诉、高频维护导致有效寿命归零
        if (d.earlyExhausted()) {
            String reason = "滤芯寿命提前耗尽。" + d.reason();
            FulfillmentCase c = caseService.obtainOpenCase(device, TicketTrigger.LIFE_EXHAUSTED_EARLY.name(), reason);
            alertService.open(device, AlertType.EARLY_EXHAUSTION, reason, c, null, null, null);
            ticketService.openTicket(device, TicketTrigger.LIFE_EXHAUSTED_EARLY, reason, c);
            if (filter != null) {
                filter.setEarlyExhausted(true);
                filter.setStatus(FilterStatus.EXHAUSTED);
                filterRepository.save(filter);
            }
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.FILTER_EARLY_EXHAUSTED, device.getDeviceCode(),
                    EventBus.payload("deviceCode", device.getDeviceCode(), "caseNo", c.getCaseNo(),
                            "decision", d.reason()));
        } else if (d.exhausted()) {
            // 正常到寿，直接预警换芯
            if (filter != null && filter.getStatus() == FilterStatus.IN_USE) {
                filter.setStatus(FilterStatus.EXHAUSTED);
                filterRepository.save(filter);
            }
            FulfillmentCase c = caseService.obtainOpenCase(device, TicketTrigger.LIFE_WARNING.name(),
                    "滤芯寿命已耗尽，需立即换芯。" + d.reason());
            alertService.open(device, AlertType.LIFE_WARNING, "滤芯寿命已耗尽（0%），需立即换芯", c, null, null, null);
            ticketService.openTicket(device, TicketTrigger.LIFE_WARNING, "寿命耗尽", c);
        } else if (d.dueSoon() && !d.earlyExhausted()) {
            // 3) 接近阈值：生成预警并安排维护师傅（不停机）
            FulfillmentCase c = caseService.obtainOpenCase(device, TicketTrigger.LIFE_WARNING.name(),
                    "滤芯寿命接近阈值。" + d.reason());
            alertService.open(device, AlertType.LIFE_WARNING,
                    "滤芯剩余有效寿命 " + d.effectiveLifePercent() + "%，接近阈值 "
                            + device.getLifeThresholdPercent() + "%，建议安排换芯。" + d.reason(),
                    c, null, null, null);
            MaintenanceTicket ticket = ticketService.openTicket(device, TicketTrigger.LIFE_WARNING,
                    "有效寿命 " + d.effectiveLifePercent() + "%", c);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.FILTER_LIFE_WARNING, device.getDeviceCode(),
                    EventBus.payload("deviceCode", device.getDeviceCode(), "effectiveLife", d.effectiveLifePercent(),
                            "threshold", device.getLifeThresholdPercent(), "urgency", d.urgency(),
                            "caseNo", c.getCaseNo(), "ticketNo", ticket.getTicketNo()));
        }
    }
}
