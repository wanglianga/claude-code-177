package com.community.water.core;

import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.Requests.DispenseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static com.community.water.config.KafkaTopicConfig.DISPENSE_TOPIC;

/**
 * 取水服务：居民扫码取水，记录小区/楼栋/取水量/扣费后余额与异味标记。
 * 设备暂停时拒绝出水并给出"为什么暂停"的解释；扣费失败不阻断记录但明确标记。
 */
@Service
public class DispenseService {

    private final DeviceRepository deviceRepository;
    private final ResidentAccountRepository accountRepository;
    private final DispenseRepository dispenseRepository;
    private final ChargeService chargeService;
    private final ComplaintService complaintService;
    private final CaseService caseService;
    private final EventBus eventBus;

    public DispenseService(DeviceRepository deviceRepository,
                           ResidentAccountRepository accountRepository,
                           DispenseRepository dispenseRepository,
                           ChargeService chargeService,
                           ComplaintService complaintService,
                           CaseService caseService,
                           EventBus eventBus) {
        this.deviceRepository = deviceRepository;
        this.accountRepository = accountRepository;
        this.dispenseRepository = dispenseRepository;
        this.chargeService = chargeService;
        this.complaintService = complaintService;
        this.caseService = caseService;
        this.eventBus = eventBus;
    }

    @Transactional
    public DispenseRecord dispense(DispenseRequest req) {
        Device device = deviceRepository.findByDeviceCode(req.deviceCode())
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + req.deviceCode()));
        ResidentAccount account = accountRepository.findByAccountNo(req.accountNo())
                .orElseThrow(() -> ApiException.notFound("账户不存在: " + req.accountNo()));
        if (!account.getCommunityId().equals(device.getCommunityId())) {
            throw ApiException.badRequest("账户与设备不属于同一小区，不能跨小区取水");
        }
        if (device.getStatus() != DeviceStatus.ACTIVE) {
            throw new ApiException(409, "设备暂停取水：" + device.getSuspensionReason()
                    + "（请待水质复检合格恢复后再取水）");
        }

        BigDecimal amount = ChargeService.round2(device.getPricePerLiter().multiply(BigDecimal.valueOf(req.liters())));
        // 设备若有未闭环履约链路（如暂停/投诉处理中，通常此时不能取水；此处兜底关联），扣费挂入该链路
        FulfillmentCase openCase = caseService.findOpenCase(device.getId());

        Charge charge = chargeService.attempt(account, device, amount, ChargeType.WATER_FEE,
                "扫码取水 " + req.liters() + "L", null, null, openCase);

        DispenseRecord d = new DispenseRecord();
        d.setDeviceId(device.getId());
        d.setDeviceCode(device.getDeviceCode());
        d.setCommunityId(device.getCommunityId());
        d.setBuilding(device.getBuilding());
        d.setAccountNo(account.getAccountNo());
        d.setDispensedAt(OffsetDateTime.now());
        d.setLiters(req.liters());
        d.setUnitPrice(device.getPricePerLiter());
        d.setAmount(amount);
        d.setBalanceAfter(account.getBalance());
        d.setChargeSucceeded(charge.getStatus() == ChargeStatus.SUCCESS);
        d.setSmellComplaint(req.smellComplaint());
        d.setChargeId(charge.getId());
        d.setCaseId(openCase == null ? null : openCase.getId());
        dispenseRepository.save(d);

        // 滤芯/设备出水量由设备遥测统一累计，避免与按次取水重复计算
        eventBus.emit(DISPENSE_TOPIC, EventType.DISPENSE_RECORDED, device.getDeviceCode(),
                EventBus.payload("deviceCode", device.getDeviceCode(), "accountNo", account.getAccountNo(),
                        "liters", req.liters(), "amount", amount.toString(),
                        "chargeSucceeded", d.isChargeSucceeded(), "chargeNo", charge.getChargeNo(),
                        "smellComplaint", req.smellComplaint()));

        if (req.smellComplaint()) {
            complaintService.register(device, account,
                    req.complaintContent() == null || req.complaintContent().isBlank()
                            ? "取水有异味" : req.complaintContent(), d.getId());
        }
        return d;
    }

    @Transactional(readOnly = true)
    public List<DispenseRecord> listForAccount(String accountNo) {
        return dispenseRepository.findByAccountNoOrderByDispensedAtDesc(accountNo);
    }

    @Transactional(readOnly = true)
    public List<DispenseRecord> listForDevice(Long deviceId) {
        return dispenseRepository.findByDeviceIdOrderByDispensedAtDesc(deviceId);
    }
}
