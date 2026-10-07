package com.community.water.core;

import com.community.water.domain.Device;
import com.community.water.domain.WaterFilter;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 换芯策略：滤芯寿命并非固定日期。综合
 * 1) 设备上报寿命 2) 按出水量折算的寿命 3) 近期投诉 4) 近期维护频率 5) 水质健康，
 * 得到用于决策的"有效剩余寿命"与换芯紧迫度。运营据此决定是否提前换芯。
 */
@Service
public class FilterStrategyService {

    /**
     * @param reportedLifePercent 设备最新上报寿命
     * @param filter              当前滤芯
     * @param recentComplaints    近期投诉数
     * @param recentReplacements  近 30 天换芯次数（维护频率）
     * @param waterHealthy        最近水质是否合格
     */
    public StrategyDecision evaluate(Device device,
                                     int reportedLifePercent,
                                     WaterFilter filter,
                                     long recentComplaints,
                                     int recentReplacements,
                                     boolean waterHealthy) {
        // 1) 出水量折算寿命
        int capacityLife = 100;
        if (filter != null && filter.getExpectedCapacityLiters() != null
                && filter.getExpectedCapacityLiters() > 0) {
            double ratio = filter.getUsedLiters() / filter.getExpectedCapacityLiters();
            capacityLife = (int) Math.round(Math.max(0, 100 * (1 - ratio)));
        }
        // 2) 取上报值与容量折算值的较小者（设备的寿命估计不可单方面信任）
        int effectiveLife = Math.min(reportedLifePercent, capacityLife);

        // 3) 投诉与水质惩罚：异味投诉集中、水质异常都加速寿命消耗
        if (recentComplaints > 0) {
            effectiveLife -= (int) Math.min(40, recentComplaints * 10);
        }
        if (!waterHealthy) {
            effectiveLife -= 30;
        }
        // 4) 维护频繁说明该点位工况差，策略更保守（更早换）
        if (recentReplacements >= 2) {
            effectiveLife -= 5;
        }
        effectiveLife = Math.max(0, effectiveLife);

        boolean dueSoon = effectiveLife <= device.getLifeThresholdPercent();
        boolean exhausted = effectiveLife <= 0;
        // 提前耗尽：有效寿命归零，但按标称容量本应还有富余（且上报也未到期）
        boolean earlyExhausted = exhausted
                && filter != null
                && capacityLife >= 40
                && reportedLifePercent >= 30
                && (recentComplaints > 0 || !waterHealthy || recentReplacements >= 2);

        String urgency;
        if (exhausted || !waterHealthy) {
            urgency = "IMMEDIATE";
        } else if (dueSoon || recentComplaints >= 2) {
            urgency = "HIGH";
        } else if (effectiveLife <= device.getLifeThresholdPercent() + 20) {
            urgency = "MEDIUM";
        } else {
            urgency = "LOW";
        }

        String reason = String.format(
                "有效寿命=%d%%（上报=%d%%，容量折算=%d%%），近30天投诉=%d，换芯=%d次，水质合格=%s；阈值=%d%%",
                effectiveLife, reportedLifePercent, capacityLife, recentComplaints,
                recentReplacements, waterHealthy, device.getLifeThresholdPercent());

        return new StrategyDecision(effectiveLife, reportedLifePercent, capacityLife,
                dueSoon, exhausted, earlyExhausted, urgency, reason);
    }

    public record StrategyDecision(int effectiveLifePercent,
                                   int reportedLifePercent,
                                   int capacityLifePercent,
                                   boolean dueSoon,
                                   boolean exhausted,
                                   boolean earlyExhausted,
                                   String urgency,
                                   String reason) {
    }

    /** 近 30 天换芯次数（由调用方传入时间列表计算）。 */
    public int countSince(List<OffsetDateTime> times, OffsetDateTime since) {
        return (int) times.stream().filter(t -> t != null && !t.isBefore(since)).count();
    }
}
