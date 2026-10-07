package com.community.water.service;

import com.community.water.config.WaterRules;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 换芯履约：师傅扫码确认旧滤芯编号、新滤芯批次、安装照片、冲洗时间和复检结果。
 * 复检合格 → 旧滤芯退役、新滤芯启用、设备恢复售水、触发维护费分摊。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplacementService {

    private final WorkOrderRepository workOrderRepository;
    private final FilterCartridgeRepository filterRepository;
    private final FilterReplacementRepository replacementRepository;
    private final RecheckRecordRepository recheckRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceEventService eventService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final BillingService billingService;
    private final WaterRules rules;

    @Transactional
    public FilterReplacement completeReplacement(String orderNo, String techNo, String oldFilterNo,
                                                 String newFilterNo, String newBatchNo, String photoUrls,
                                                 int flushMinutes, Double recheckTds, Double recheckChlorine) {
        WorkOrder order = workOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> BusinessException.notFound("工单 " + orderNo));
        if (order.getType() != WorkOrderType.FILTER_REPLACEMENT) {
            throw new BusinessException("工单 " + orderNo + " 不是换芯工单");
        }
        if (order.getStatus() != WorkOrderStatus.ARRIVED) {
            throw BusinessException.conflict("工单状态为 " + order.getStatus() + "，需先确认到场");
        }
        if (!order.getTechnician().getTechNo().equals(techNo)) {
            throw new BusinessException(403, "该工单未派给师傅 " + techNo);
        }
        Device device = order.getDevice();

        // 旧滤芯扫码校验：必须与设备当前在用滤芯一致，防止"假换芯"
        FilterCartridge oldFilter = filterRepository.findByDeviceAndStatus(device, FilterStatus.ACTIVE)
                .orElseThrow(() -> BusinessException.conflict("设备无在用滤芯，无法换芯"));
        if (!oldFilter.getFilterNo().equals(oldFilterNo)) {
            throw BusinessException.conflict("旧滤芯编号 " + oldFilterNo + " 与设备在用滤芯 "
                    + oldFilter.getFilterNo() + " 不一致，换芯被拒绝");
        }
        // 新滤芯编号不得复用
        if (filterRepository.findByFilterNo(newFilterNo).isPresent()) {
            throw BusinessException.conflict("新滤芯编号 " + newFilterNo + " 已存在，疑似复用旧滤芯");
        }
        if (photoUrls == null || photoUrls.isBlank()) {
            throw new BusinessException("必须上传安装照片");
        }
        if (flushMinutes < rules.filter().minFlushMinutes()) {
            throw new BusinessException("冲洗时间不足 " + rules.filter().minFlushMinutes()
                    + " 分钟，不得完成换芯");
        }

        // 复检结果判定
        boolean pass = recheckTds != null && recheckChlorine != null
                && recheckTds <= rules.quality().tdsMax()
                && recheckChlorine >= rules.quality().chlorineMin()
                && recheckChlorine <= rules.quality().chlorineMax();

        FilterReplacement replacement = new FilterReplacement();
        replacement.setWorkOrder(order);
        replacement.setDevice(device);
        replacement.setOldFilterNo(oldFilterNo);
        replacement.setNewFilterNo(newFilterNo);
        replacement.setNewBatchNo(newBatchNo);
        replacement.setPhotoUrls(photoUrls);
        replacement.setFlushMinutes(flushMinutes);
        replacement.setRecheckTds(recheckTds);
        replacement.setRecheckChlorine(recheckChlorine);
        replacement.setRecheckResult(pass ? RecheckResult.PASS : RecheckResult.FAIL);
        replacementRepository.save(replacement);

        RecheckRecord recheck = new RecheckRecord();
        recheck.setDevice(device);
        recheck.setReplacementId(replacement.getId());
        recheck.setOrderNo(orderNo);
        recheck.setKind("REPLACEMENT");
        recheck.setTds(recheckTds);
        recheck.setResidualChlorine(recheckChlorine);
        recheck.setResult(pass ? RecheckResult.PASS : RecheckResult.FAIL);
        recheck.setInspector(order.getTechnician().getName());
        recheck.setNote("换芯后复检");
        recheckRepository.save(recheck);
        eventService.record(device, "RECHECK", orderNo, String.format(
                "换芯复检：TDS=%.1f，余氯=%.2f，结果=%s", recheckTds, recheckChlorine, pass ? "合格" : "不合格"));

        if (pass) {
            // 旧滤芯退役，新滤芯启用
            oldFilter.setStatus(FilterStatus.REPLACED);
            oldFilter.setReplacedAt(LocalDateTime.now());
            filterRepository.save(oldFilter);

            FilterCartridge newFilter = new FilterCartridge();
            newFilter.setFilterNo(newFilterNo);
            newFilter.setBatchNo(newBatchNo);
            newFilter.setDevice(device);
            newFilter.setRatedCapacityLiters(oldFilter.getRatedCapacityLiters());
            newFilter.setUsedLiters(0);
            newFilter.setLifePercent(100);
            newFilter.setStatus(FilterStatus.ACTIVE);
            filterRepository.save(newFilter);

            device.setStatus(DeviceStatus.NORMAL);
            device.setPauseReason(null);
            device.setLastMaintenanceAt(LocalDateTime.now());
            deviceRepository.save(device);

            order.setStatus(WorkOrderStatus.COMPLETED);
            order.setCompletedAt(LocalDateTime.now());
            workOrderRepository.save(order);

            // 解除相关预警
            alertService.resolve(device, AlertType.FILTER_LIFE_LOW, "换芯完成");
            alertService.resolve(device, AlertType.FILTER_EXHAUSTED, "换芯完成");
            alertService.resolve(device, AlertType.WATER_QUALITY_ABNORMAL, "换芯复检合格");
            alertService.resolve(device, AlertType.COMPLAINT_CLUSTER, "换芯复检合格");

            eventService.record(device, "REPLACEMENT", orderNo, String.format(
                    "换芯完成：旧滤芯 %s → 新滤芯 %s（批次 %s），冲洗 %d 分钟，复检合格，设备恢复售水",
                    oldFilterNo, newFilterNo, newBatchNo, flushMinutes));
            eventService.record(device, "DEVICE_RESUMED", null, "换芯复检合格，恢复售水");
            notificationService.notify(NotificationService.PROPERTY,
                    device.getCommunity() + "/" + device.getBuilding(), "REPLACEMENT_DONE",
                    "设备 " + device.getDeviceNo() + " 换芯完成（新批次 " + newBatchNo + "），复检合格已恢复售水");

            // 维护费按楼栋分摊（免费返工工单不收费）
            if (!order.isFreeOfCharge()) {
                billingService.shareMaintenanceFee(device, order);
            }
        } else {
            // 复检不合格：设备保持停售，生成新预警继续处理
            order.setStatus(WorkOrderStatus.COMPLETED);
            order.setCompletedAt(LocalDateTime.now());
            workOrderRepository.save(order);
            alertService.raise(device, AlertType.WATER_QUALITY_ABNORMAL, "CRITICAL",
                    String.format("换芯后复检不合格：TDS=%.1f，余氯=%.2f，设备继续停售", recheckTds, recheckChlorine));
            eventService.record(device, "REPLACEMENT", orderNo,
                    "换芯完成但复检不合格，设备保持停售");
        }
        return replacement;
    }
}
