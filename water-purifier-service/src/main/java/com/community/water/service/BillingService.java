package com.community.water.service;

import com.community.water.entity.*;
import com.community.water.repository.ChargeRecordRepository;
import com.community.water.repository.ResidentAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 收费：换芯维护费按楼栋居民账户均摊；余额不足的账户记扣费失败并通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    /** 单次换芯维护费总额（元），按楼栋账户均摊 */
    public static final BigDecimal MAINTENANCE_FEE_TOTAL = new BigDecimal("120.00");

    private final ResidentAccountRepository accountRepository;
    private final ChargeRecordRepository chargeRepository;
    private final DeviceEventService eventService;
    private final NotificationService notificationService;
    private final AlertService alertService;

    /**
     * 记录取水扣费失败（独立事务）：即使外层取水事务回滚，失败记录、预警与通知仍然保留。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChargeRecord recordFailedWaterCharge(Device device, ResidentAccount account,
                                                BigDecimal amount, String reason) {
        ChargeRecord failed = new ChargeRecord();
        failed.setChargeNo("CG" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        failed.setAccount(account);
        failed.setType(ChargeType.WATER_FEE);
        failed.setAmount(amount);
        failed.setStatus(ChargeStatus.FAILED);
        failed.setFailReason(reason);
        failed.setDescription("取水扣费失败");
        chargeRepository.save(failed);
        alertService.raise(device, AlertType.CHARGE_FAILED, "WARN",
                "居民 " + account.getName() + " 取水扣费失败：" + reason);
        notificationService.notify(NotificationService.RESIDENT, account.getAccountNo(), "CHARGE_FAILED",
                "您在设备 " + device.getDeviceNo() + " 取水扣费失败：" + reason + "，请充值后重试");
        eventService.record(device, "CHARGE_FAILED", failed.getChargeNo(),
                "账户 " + account.getAccountNo() + " 扣费失败：" + reason);
        return failed;
    }

    /**
     * 物业要求按楼栋分摊维护费：设备所在楼栋的全部居民账户均摊。
     *
     * @return 每户分摊金额与扣费结果
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<ChargeRecord> shareMaintenanceFee(Device device, WorkOrder order) {
        List<ResidentAccount> residents = accountRepository.findByCommunityAndBuilding(
                device.getCommunity(), device.getBuilding());
        if (residents.isEmpty()) {
            log.warn("no residents to share maintenance fee for {}/{}",
                    device.getCommunity(), device.getBuilding());
            return List.of();
        }
        BigDecimal share = MAINTENANCE_FEE_TOTAL
                .divide(BigDecimal.valueOf(residents.size()), 2, RoundingMode.HALF_UP);
        List<ChargeRecord> records = new ArrayList<>();
        for (ResidentAccount account : residents) {
            ChargeRecord record = new ChargeRecord();
            record.setChargeNo("CG" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            record.setAccount(account);
            record.setType(ChargeType.MAINTENANCE_SHARE);
            record.setAmount(share);
            record.setOrderNo(order.getOrderNo());
            record.setDescription(String.format("设备 %s 换芯维护费 %s 元按楼栋 %d 户均摊",
                    device.getDeviceNo(), MAINTENANCE_FEE_TOTAL, residents.size()));
            if (account.getBalance().compareTo(share) >= 0) {
                account.setBalance(account.getBalance().subtract(share));
                accountRepository.save(account);
                record.setStatus(ChargeStatus.SUCCESS);
                eventService.record(device, "CHARGE", record.getChargeNo(),
                        "维护费分摊扣费 " + share + " 元（账户 " + account.getAccountNo() + "）");
                notificationService.notify(NotificationService.RESIDENT, account.getAccountNo(),
                        "MAINTENANCE_SHARE", "设备 " + device.getDeviceNo() + " 换芯维护费分摊 "
                                + share + " 元已扣款（楼栋 " + residents.size() + " 户均摊 "
                                + MAINTENANCE_FEE_TOTAL + " 元）");
            } else {
                record.setStatus(ChargeStatus.FAILED);
                record.setFailReason("账户余额不足（余额 " + account.getBalance() + " 元）");
                eventService.record(device, "CHARGE_FAILED", record.getChargeNo(),
                        "维护费分摊扣费失败（账户 " + account.getAccountNo() + " 余额不足）");
                notificationService.notify(NotificationService.RESIDENT, account.getAccountNo(),
                        "CHARGE_FAILED", "设备 " + device.getDeviceNo() + " 维护费分摊 "
                                + share + " 元扣款失败：余额不足，请充值");
            }
            chargeRepository.save(record);
            records.add(record);
        }
        return records;
    }
}
