package com.community.water.service;

import com.community.water.config.WaterRules;
import com.community.water.entity.Device;
import com.community.water.entity.FilterCartridge;
import com.community.water.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 滤芯寿命动态计算单元测试：寿命并非固定日期 */
class FilterLifeServiceTest {

    private FilterLifeService service;
    private ComplaintRepository complaintRepository;

    @BeforeEach
    void setUp() {
        WaterRules rules = new WaterRules(
                new WaterRules.Pricing(new BigDecimal("0.30")),
                new WaterRules.Filter(15, 1, 10),
                new WaterRules.Quality(100, 0.05, 2.0, 50),
                new WaterRules.Complaint(3, 24, 7),
                new WaterRules.Workorder(4, 30),
                new WaterRules.Topics("t", "i", "a", "n"));
        complaintRepository = mock(ComplaintRepository.class);
        service = new FilterLifeService(rules, complaintRepository);
    }

    @Test
    void tdsAboveBaseAcceleratesConsumption() {
        // TDS=150，超出基准 100 → 加速 50%
        assertThat(service.qualityStressFactor(150.0)).isEqualTo(1.5);
        // TDS 正常 → 不加速
        assertThat(service.qualityStressFactor(40.0)).isEqualTo(1.0);
        // 极端水质 → 封顶 2 倍
        assertThat(service.qualityStressFactor(1000.0)).isEqualTo(2.0);
    }

    @Test
    void consumeReducesLifeByWaterQualityWeightedUsage() {
        FilterCartridge filter = filter(10000, 0);
        when(complaintRepository.countByDeviceAndCreatedAtAfter(any(), any())).thenReturn(0L);

        service.consume(filter, 1000, 150.0); // 1000L × 1.5 = 1500L 有效消耗

        assertThat(filter.getUsedLiters()).isEqualTo(1500.0);
        assertThat(filter.getLifePercent()).isEqualTo(85.0);
    }

    @Test
    void complaintsAndMaintenanceGapReduceLife() {
        Device device = new Device();
        device.setInstalledAt(LocalDateTime.now().minusDays(200));
        device.setLastMaintenanceAt(LocalDateTime.now().minusDays(150));
        FilterCartridge filter = filter(10000, 2000);
        filter.setDevice(device);
        // 近 24 小时 5 起投诉 → 额外扣 10%
        when(complaintRepository.countByDeviceAndCreatedAtAfter(any(), any())).thenReturn(5L);

        service.recomputeLifePercent(filter);

        // 基础 80% - 投诉 10% - 维护超期((150-90)/30+1)*3=9% → 61%
        assertThat(filter.getLifePercent()).isEqualTo(61.0);
    }

    @Test
    void waterQualityJudgement() {
        assertThat(service.isWaterQualityAbnormal(150.0, 0.5)).isTrue();   // TDS 超标
        assertThat(service.isWaterQualityAbnormal(50.0, 0.01)).isTrue();   // 余氯过低
        assertThat(service.isWaterQualityAbnormal(50.0, 3.0)).isTrue();    // 余氯过高
        assertThat(service.isWaterQualityAbnormal(50.0, 0.5)).isFalse();   // 正常
    }

    private FilterCartridge filter(double capacity, double used) {
        Device device = new Device();
        device.setInstalledAt(LocalDateTime.now().minusDays(10));
        device.setLastMaintenanceAt(LocalDateTime.now().minusDays(5));
        FilterCartridge f = new FilterCartridge();
        f.setDevice(device);
        f.setRatedCapacityLiters(capacity);
        f.setUsedLiters(used);
        f.setLifePercent(100);
        return f;
    }
}
