package com.community.water.service;

import com.community.water.config.WaterRules;
import com.community.water.entity.*;
import com.community.water.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 运营端换芯策略：按设备健康（滤芯寿命、水质、投诉、维护频率）动态决定。
 */
@Service
@RequiredArgsConstructor
public class OperationsService {

    private final DeviceRepository deviceRepository;
    private final FilterCartridgeRepository filterRepository;
    private final DeviceTelemetryRepository telemetryRepository;
    private final ComplaintRepository complaintRepository;
    private final WaterIntakeRepository intakeRepository;
    private final WaterRules rules;

    public record DeviceStrategy(
            String deviceNo,
            String community,
            String building,
            String deviceStatus,
            Double filterLifePercent,
            Double latestTds,
            Double latestChlorine,
            long complaints24h,
            double dailyUsageLiters,
            Integer estimatedDaysRemaining,
            int healthScore,
            String recommendedAction
    ) {}

    @Transactional(readOnly = true)
    public List<DeviceStrategy> replacementStrategy() {
        List<DeviceStrategy> result = new ArrayList<>();
        for (Device device : deviceRepository.findAll()) {
            FilterCartridge filter = filterRepository
                    .findByDeviceAndStatus(device, FilterStatus.ACTIVE).orElse(null);
            DeviceTelemetry latest = telemetryRepository
                    .findTopByDeviceOrderByReportedAtDesc(device).orElse(null);
            long complaints = complaintRepository.countByDeviceAndCreatedAtAfter(
                    device, LocalDateTime.now().minusHours(24));

            // 近 7 天日均取水量
            double dailyUsage = intakeRepository
                    .findByDeviceAndCreatedAtAfter(device, LocalDateTime.now().minusDays(7))
                    .stream().mapToDouble(WaterIntake::getAmountLiters).sum() / 7.0;

            Double life = filter != null ? filter.getLifePercent() : null;
            Integer daysRemaining = null;
            if (filter != null && dailyUsage > 0) {
                double remainingLiters = filter.getRatedCapacityLiters() - filter.getUsedLiters();
                daysRemaining = (int) Math.max(0, remainingLiters / dailyUsage);
            }

            // 健康分：寿命 50 分 + 水质 30 分 + 投诉 20 分
            int score = 0;
            if (life != null) {
                score += (int) (life * 0.5);
            }
            boolean qualityOk = latest == null || (latest.getTds() != null
                    && latest.getTds() <= rules.quality().tdsMax()
                    && latest.getResidualChlorine() != null
                    && latest.getResidualChlorine() >= rules.quality().chlorineMin()
                    && latest.getResidualChlorine() <= rules.quality().chlorineMax());
            score += qualityOk ? 30 : 0;
            score += complaints == 0 ? 20 : (complaints < 3 ? 10 : 0);

            String action = recommend(device, life, qualityOk, complaints, daysRemaining);
            result.add(new DeviceStrategy(
                    device.getDeviceNo(), device.getCommunity(), device.getBuilding(),
                    device.getStatus().name(), life,
                    latest != null ? latest.getTds() : null,
                    latest != null ? latest.getResidualChlorine() : null,
                    complaints, Math.round(dailyUsage * 10.0) / 10.0,
                    daysRemaining, score, action));
        }
        return result;
    }

    private String recommend(Device device, Double life, boolean qualityOk,
                             long complaints, Integer daysRemaining) {
        if (device.getStatus() == DeviceStatus.PAUSED) {
            return "设备停售中：优先完成复检/换芯恢复供水";
        }
        if (!qualityOk) {
            return "水质异常：立即停售并安排换芯复检";
        }
        if (complaints >= rules.complaint().clusterThreshold()) {
            return "投诉聚集：提前换芯并上门解释";
        }
        if (life != null && life <= rules.filter().warnPercent()) {
            return "滤芯寿命不足：48 小时内安排换芯";
        }
        if (daysRemaining != null && daysRemaining <= 14) {
            return "按当前取水强度预计 " + daysRemaining + " 天后耗尽：预约换芯";
        }
        return "运行正常：按计划巡检";
    }
}
