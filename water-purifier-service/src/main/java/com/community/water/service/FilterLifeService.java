package com.community.water.service;

import com.community.water.config.WaterRules;
import com.community.water.entity.Device;
import com.community.water.entity.FilterCartridge;
import com.community.water.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 滤芯寿命动态计算：寿命并非固定日期。
 * 有效消耗 = 实际出水量 × 水质压力系数（TDS 越高消耗越快），
 * 投诉与维护频率进一步折算剩余寿命。
 */
@Service
@RequiredArgsConstructor
public class FilterLifeService {

    private final WaterRules rules;
    private final ComplaintRepository complaintRepository;

    /** 水质压力系数：TDS 每高于基准 50，消耗加速 25%，上限 2 倍 */
    public double qualityStressFactor(Double tds) {
        if (tds == null) {
            return 1.0;
        }
        double base = rules.quality().tdsStressBase();
        double factor = 1.0 + Math.max(0, tds - base) / base * 0.25;
        return Math.min(factor, 2.0);
    }

    /**
     * 根据新增出水量与当前水质，扣减滤芯寿命。
     *
     * @param deltaLiters 本次新增出水量
     * @param tds         当前 TDS
     */
    public void consume(FilterCartridge filter, double deltaLiters, Double tds) {
        if (deltaLiters <= 0) {
            return;
        }
        double effective = deltaLiters * qualityStressFactor(tds);
        filter.setUsedLiters(filter.getUsedLiters() + effective);
        recomputeLifePercent(filter);
    }

    /** 重新计算剩余寿命百分比（含投诉与维护频率折算） */
    public void recomputeLifePercent(FilterCartridge filter) {
        double raw = 100.0 * (1.0 - filter.getUsedLiters() / filter.getRatedCapacityLiters());

        Device device = filter.getDevice();
        // 投诉折算：近 24 小时每起投诉额外扣 2 个百分点
        long complaints = complaintRepository.countByDeviceAndCreatedAtAfter(
                device, LocalDateTime.now().minusHours(24));
        raw -= complaints * 2.0;

        // 维护频率折算：超过 90 天未维护，每 30 天额外扣 3 个百分点
        LocalDateTime lastMaintenance = device.getLastMaintenanceAt() != null
                ? device.getLastMaintenanceAt() : device.getInstalledAt();
        if (lastMaintenance != null) {
            long days = Duration.between(lastMaintenance, LocalDateTime.now()).toDays();
            if (days > 90) {
                raw -= ((days - 90) / 30 + 1) * 3.0;
            }
        }

        filter.setLifePercent(Math.max(0, Math.min(100, raw)));
    }

    /** 水质是否异常 */
    public boolean isWaterQualityAbnormal(Double tds, Double chlorine) {
        if (tds != null && tds > rules.quality().tdsMax()) {
            return true;
        }
        if (chlorine != null && (chlorine < rules.quality().chlorineMin()
                || chlorine > rules.quality().chlorineMax())) {
            return true;
        }
        return false;
    }
}
