package com.community.water.core;

import com.community.water.config.AppProperties;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 设备健康画像：供运营按健康度与投诉决定动态换芯策略。 */
@Service
public class DeviceHealthService {

    private final DeviceRepository deviceRepository;
    private final WaterFilterRepository filterRepository;
    private final TelemetryRepository telemetryRepository;
    private final ComplaintRepository complaintRepository;
    private final FilterReplacementRepository replacementRepository;
    private final FilterStrategyService strategyService;
    private final AppProperties props;

    public DeviceHealthService(DeviceRepository deviceRepository,
                               WaterFilterRepository filterRepository,
                               TelemetryRepository telemetryRepository,
                               ComplaintRepository complaintRepository,
                               FilterReplacementRepository replacementRepository,
                               FilterStrategyService strategyService,
                               AppProperties props) {
        this.deviceRepository = deviceRepository;
        this.filterRepository = filterRepository;
        this.telemetryRepository = telemetryRepository;
        this.complaintRepository = complaintRepository;
        this.replacementRepository = replacementRepository;
        this.strategyService = strategyService;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> healthOf(String deviceCode) {
        Device device = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceCode));
        OffsetDateTime now = OffsetDateTime.now();

        WaterFilter filter = filterRepository
                .findFirstByDeviceIdAndStatusOrderByInstalledAtDesc(device.getId(), FilterStatus.IN_USE)
                .orElse(null);
        TelemetryRecord latest = telemetryRepository
                .findFirstByDeviceIdOrderByEventTimeDesc(device.getId()).orElse(null);
        int reportedLife = latest == null ? (filter == null ? 100 : filter.getLifePercent())
                : latest.getFilterLifePercent();
        boolean waterHealthy = latest == null
                || (latest.getTds() <= device.getTdsLimit()
                && latest.getChlorine() <= device.getChlorineLimit()
                && !(latest.getFaultCode() != null && !latest.getFaultCode().isBlank()
                && !"0".equals(latest.getFaultCode())));
        long openComplaints = complaintRepository.countByDeviceIdAndStatusAndCreatedAtAfter(
                device.getId(), ComplaintStatus.OPEN, now.minusHours(props.complaintWindowHours()));
        OffsetDateTime since30 = now.minusDays(30);
        int replacements30 = (int) replacementRepository.findByDeviceIdOrderByReplacedAtDesc(device.getId())
                .stream().filter(r -> r.getReplacedAt() != null && !r.getReplacedAt().isBefore(since30)).count();

        FilterStrategyService.StrategyDecision d = strategyService.evaluate(
                device, reportedLife, filter, openComplaints, replacements30, waterHealthy);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceCode", device.getDeviceCode());
        m.put("status", device.getStatus().name());
        m.put("building", device.getBuilding());
        m.put("cumulativeOutputLiters", device.getCumulativeOutputLiters());
        m.put("waterHealthy", waterHealthy);
        m.put("latestTelemetry", latest == null ? null : Map.of(
                "eventTime", latest.getEventTime().toString(),
                "tds", latest.getTds(), "chlorine", latest.getChlorine(),
                "flowRate", latest.getFlowRate(), "faultCode", String.valueOf(latest.getFaultCode()),
                "reportedLifePercent", latest.getFilterLifePercent()));
        m.put("currentFilter", filter == null ? null : Map.of(
                "serialNo", filter.getFilterSerialNo(), "batchNo", filter.getBatchNo(),
                "usedLiters", filter.getUsedLiters(),
                "expectedCapacityLiters", filter.getExpectedCapacityLiters(),
                "lifePercent", filter.getLifePercent(), "earlyExhausted", filter.isEarlyExhausted()));
        m.put("factors", Map.of(
                "reportedLifePercent", d.reportedLifePercent(),
                "capacityLifePercent", d.capacityLifePercent(),
                "openComplaints24h", openComplaints,
                "replacements30d", replacements30));
        m.put("decision", Map.of(
                "effectiveLifePercent", d.effectiveLifePercent(),
                "thresholdPercent", device.getLifeThresholdPercent(),
                "dueSoon", d.dueSoon(), "exhausted", d.exhausted(),
                "earlyExhausted", d.earlyExhausted(), "urgency", d.urgency()));
        m.put("explanation", d.reason());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> allHealth() {
        return deviceRepository.findAll().stream()
                .map(d -> healthOf(d.getDeviceCode())).toList();
    }
}
