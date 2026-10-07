package com.community.water.service;

import com.community.water.config.WaterRules;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.kafka.msg.TelemetryMessage;
import com.community.water.repository.DeviceRepository;
import com.community.water.repository.DeviceTelemetryRepository;
import com.community.water.repository.FilterCartridgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 遥测处理：设备持续上报 → 更新滤芯寿命 → 触发预警/工单/停售。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final DeviceRepository deviceRepository;
    private final DeviceTelemetryRepository telemetryRepository;
    private final FilterCartridgeRepository filterRepository;
    private final FilterLifeService filterLifeService;
    private final AlertService alertService;
    private final WorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final DeviceEventService eventService;
    private final WaterRules rules;

    @Transactional
    public void process(TelemetryMessage msg) {
        Device device = deviceRepository.findByDeviceNo(msg.deviceNo())
                .orElseThrow(() -> BusinessException.notFound("设备 " + msg.deviceNo()));

        // 上一条遥测（计算出水量增量用）
        Double previousTotal = telemetryRepository.findTopByDeviceOrderByReportedAtDesc(device)
                .map(DeviceTelemetry::getTotalOutputLiters).orElse(null);

        // 1. 保存遥测
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDevice(device);
        t.setFilterLifePercent(msg.filterLifePercent());
        t.setTotalOutputLiters(msg.totalOutputLiters());
        t.setTds(msg.tds());
        t.setResidualChlorine(msg.residualChlorine());
        t.setFlowRate(msg.flowRate());
        t.setFaultCode(msg.faultCode());
        t.setLastMaintenanceAt(msg.lastMaintenanceAt());
        telemetryRepository.save(t);
        eventService.record(device, "TELEMETRY", null, String.format(
                "遥测上报：TDS=%.1f，余氯=%.2f，累计出水=%.1fL，故障码=%s",
                nz(msg.tds()), nz(msg.residualChlorine()), nz(msg.totalOutputLiters()),
                msg.faultCode() == null ? "无" : msg.faultCode()));

        // 2. 滤芯寿命动态消耗（按出水量增量 × 水质压力系数）
        Optional<FilterCartridge> activeOpt = filterRepository.findByDeviceAndStatus(device, FilterStatus.ACTIVE);
        if (activeOpt.isPresent() && msg.totalOutputLiters() != null) {
            FilterCartridge filter = activeOpt.get();
            double delta = previousTotal == null ? 0 : Math.max(0, msg.totalOutputLiters() - previousTotal);
            if (delta > 0) {
                filterLifeService.consume(filter, delta, msg.tds());
                filterRepository.save(filter);
                eventService.record(device, "FILTER_LIFE", filter.getFilterNo(), String.format(
                        "滤芯消耗：新增出水 %.1fL（水质系数 %.2f），剩余寿命 %.1f%%",
                        delta, filterLifeService.qualityStressFactor(msg.tds()), filter.getLifePercent()));
            }
            evaluateFilterLife(device, filter);
        }

        // 3. 水质异常 → 暂停售水并通知物业
        if (filterLifeService.isWaterQualityAbnormal(msg.tds(), msg.residualChlorine())) {
            Alert alert = alertService.raise(device, AlertType.WATER_QUALITY_ABNORMAL, "CRITICAL",
                    String.format("水质异常：TDS=%.1f（上限 %d），余氯=%.2f（合格区间 %.2f-%.2f）",
                            nz(msg.tds()), rules.quality().tdsMax(), nz(msg.residualChlorine()),
                            rules.quality().chlorineMin(), rules.quality().chlorineMax()));
            pauseDevice(device, "水质异常（TDS/余氯超标），已暂停售水，待换芯复检合格后恢复");
            notificationService.notify(NotificationService.PROPERTY,
                    device.getCommunity() + "/" + device.getBuilding(), "WATER_QUALITY_ABNORMAL",
                    "设备 " + device.getDeviceNo() + " 水质异常已暂停售水：" + alert.getMessage());
            workOrderService.createOrder(device, WorkOrderType.RECHECK, alert.getAlertNo(), true,
                    "水质异常复检");
        }

        // 4. 故障码 → 维修工单
        if (msg.faultCode() != null && !msg.faultCode().isBlank()) {
            Alert alert = alertService.raise(device, AlertType.DEVICE_FAULT, "WARN",
                    "设备故障码：" + msg.faultCode());
            workOrderService.createOrder(device, WorkOrderType.REPAIR, alert.getAlertNo(), false,
                    "故障码 " + msg.faultCode() + " 维修");
        }
    }

    /** 滤芯寿命评估：接近阈值预警并安排换芯；耗尽则停售 */
    private void evaluateFilterLife(Device device, FilterCartridge filter) {
        double life = filter.getLifePercent();
        if (life <= rules.filter().exhaustedPercent()) {
            filter.setStatus(FilterStatus.EXHAUSTED);
            filterRepository.save(filter);
            Alert alert = alertService.raise(device, AlertType.FILTER_EXHAUSTED, "CRITICAL",
                    String.format("滤芯寿命提前耗尽（剩余 %.1f%%），已暂停售水", life));
            pauseDevice(device, "滤芯寿命耗尽，已暂停售水，等待换芯");
            notificationService.notify(NotificationService.PROPERTY,
                    device.getCommunity() + "/" + device.getBuilding(), "FILTER_EXHAUSTED",
                    "设备 " + device.getDeviceNo() + " 滤芯耗尽已暂停售水");
            workOrderService.createOrder(device, WorkOrderType.FILTER_REPLACEMENT,
                    alert.getAlertNo(), false, "滤芯耗尽紧急换芯");
        } else if (life <= rules.filter().warnPercent()) {
            Alert alert = alertService.raise(device, AlertType.FILTER_LIFE_LOW, "WARN",
                    String.format("滤芯寿命 %.1f%% 接近阈值 %d%%，请安排换芯",
                            life, rules.filter().warnPercent()));
            workOrderService.createOrder(device, WorkOrderType.FILTER_REPLACEMENT,
                    alert.getAlertNo(), false, "滤芯寿命不足预防性换芯");
        }
    }

    /** 暂停售水（幂等） */
    @Transactional
    public void pauseDevice(Device device, String reason) {
        if (device.getStatus() != DeviceStatus.PAUSED) {
            device.setStatus(DeviceStatus.PAUSED);
            device.setPauseReason(reason);
            deviceRepository.save(device);
            eventService.record(device, "DEVICE_PAUSED", null, "暂停售水：" + reason);
        } else {
            device.setPauseReason(reason);
            deviceRepository.save(device);
        }
    }

    private double nz(Double v) {
        return v == null ? 0 : v;
    }
}
