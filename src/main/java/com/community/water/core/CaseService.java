package com.community.water.core;

import com.community.water.domain.Device;
import com.community.water.domain.FulfillmentCase;
import com.community.water.repo.Repositories.FulfillmentCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;

/**
 * 设备履约链路（Case）服务：同一设备上发生的寿命预警、水质异常、投诉聚集、
 * 迟到、换芯、复检、收费、发票问题都收敛到同一条未闭环链路。
 */
@Service
public class CaseService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final FulfillmentCaseRepository caseRepository;
    private final EventBus eventBus;

    public CaseService(FulfillmentCaseRepository caseRepository, EventBus eventBus) {
        this.caseRepository = caseRepository;
        this.eventBus = eventBus;
    }

    /** 设备上存在未闭环链路则复用，使后续异常挂入同一链路；否则新建。 */
    @Transactional
    public FulfillmentCase obtainOpenCase(Device device, String primaryReason, String summary) {
        return caseRepository.findByDeviceIdOrderByCreatedAtDesc(device.getId()).stream()
                .filter(c -> !"CLOSED".equals(c.getStage()))
                .findFirst()
                .orElseGet(() -> createCase(device, primaryReason, summary));
    }

    /** 查找设备当前未闭环链路（可能为 null）。 */
    @Transactional(readOnly = true)
    public FulfillmentCase findOpenCase(Long deviceId) {
        return caseRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .filter(c -> !"CLOSED".equals(c.getStage()))
                .findFirst()
                .orElse(null);
    }

    /** 设备最近一条链路（含已闭环），收费/发票需要挂回原链路时使用。 */
    @Transactional(readOnly = true)
    public FulfillmentCase findLatestCase(Long deviceId) {
        return caseRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public FulfillmentCase createCase(Device device, String primaryReason, String summary) {
        FulfillmentCase c = new FulfillmentCase();
        c.setCaseNo("CASE-" + OffsetDateTime.now().format(NO_FMT) + "-" + device.getDeviceCode()
                + "-" + ThreadLocalRandom.current().nextInt(100, 999));
        c.setDeviceId(device.getId());
        c.setDeviceCode(device.getDeviceCode());
        c.setCommunityId(device.getCommunityId());
        c.setBuilding(device.getBuilding());
        c.setPrimaryReason(primaryReason);
        c.setSummary(summary);
        c.setStage("OPEN");
        caseRepository.save(c);
        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.CASE_OPENED, c.getCaseNo(), EventBus.payload(
                "caseNo", c.getCaseNo(), "deviceCode", device.getDeviceCode(),
                "reason", primaryReason, "summary", summary));
        return c;
    }

    @Transactional
    public void updateStage(FulfillmentCase c, String stage) {
        c.setStage(stage);
        caseRepository.save(c);
    }

    /** 已闭环链路又出现费用等遗留问题（如楼栋分摊欠费）时重新打开。 */
    @Transactional
    public FulfillmentCase reopenIfClosed(FulfillmentCase c, String reason) {
        if (c != null && "CLOSED".equals(c.getStage())) {
            c.setStage("BILLING_PENDING");
            c.setClosedAt(null);
            c.setBillingSettled(false);
            c.setCloseNote(null);
            c.setSummary(c.getSummary() + " | 重新打开：" + reason);
            caseRepository.save(c);
        }
        return c;
    }

    @Transactional
    public FulfillmentCase requireByNo(String caseNo) {
        return caseRepository.findByCaseNo(caseNo)
                .orElseThrow(() -> new com.community.water.support.ApiException(404, "履约链路不存在: " + caseNo));
    }

    @Transactional(readOnly = true)
    public List<FulfillmentCase> listByCommunity(Long communityId) {
        return caseRepository.findByCommunityIdOrderByCreatedAtDesc(communityId);
    }

    @Transactional(readOnly = true)
    public List<FulfillmentCase> listAll() {
        return caseRepository.findAll();
    }
}
