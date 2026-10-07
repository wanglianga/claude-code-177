package com.community.water.service;

import com.community.water.config.WaterRules;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.kafka.KafkaProducerService;
import com.community.water.kafka.msg.IntakeMessage;
import com.community.water.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 居民扫码取水：校验设备状态 → 扣费 → 记录 → 异味投诉处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeService {

    private final DeviceRepository deviceRepository;
    private final ResidentAccountRepository accountRepository;
    private final WaterIntakeRepository intakeRepository;
    private final ChargeRecordRepository chargeRepository;
    private final ComplaintRepository complaintRepository;
    private final FilterReplacementRepository replacementRepository;
    private final KafkaProducerService producer;
    private final AlertService alertService;
    private final WorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final DeviceEventService eventService;
    private final BillingService billingService;
    private final WaterRules rules;

    @Transactional
    public WaterIntake intake(String deviceNo, String accountNo, double amountLiters, boolean odorComplaint) {
        Device device = deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> BusinessException.notFound("设备 " + deviceNo));
        ResidentAccount account = accountRepository.findByAccountNo(accountNo)
                .orElseThrow(() -> BusinessException.notFound("账户 " + accountNo));

        // 设备暂停售水 → 拒绝并告知原因（居民端"为什么暂停"）
        if (device.getStatus() != DeviceStatus.NORMAL) {
            throw BusinessException.conflict("设备暂停售水：" +
                    (device.getPauseReason() != null ? device.getPauseReason() : "维护中"));
        }
        if (amountLiters <= 0 || amountLiters > 100) {
            throw new BusinessException("取水量必须在 0-100 升之间");
        }

        BigDecimal unitPrice = rules.pricing().unitPricePerLiter();
        BigDecimal fee = unitPrice.multiply(BigDecimal.valueOf(amountLiters))
                .setScale(2, RoundingMode.HALF_UP);

        // 扣费：余额不足 → 独立事务记录扣费失败 + 预警 + 通知居民（不随本事务回滚）
        if (account.getBalance().compareTo(fee) < 0) {
            String reason = "账户余额不足（余额 " + account.getBalance() + " 元，需 " + fee + " 元）";
            billingService.recordFailedWaterCharge(device, account, fee, reason);
            throw BusinessException.conflict("账户余额不足，请先充值（余额 " + account.getBalance() + " 元）");
        }

        account.setBalance(account.getBalance().subtract(fee));
        accountRepository.save(account);

        WaterIntake intake = new WaterIntake();
        intake.setIntakeNo("IN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        intake.setDevice(device);
        intake.setAccount(account);
        intake.setCommunity(device.getCommunity());
        intake.setBuilding(device.getBuilding());
        intake.setAmountLiters(amountLiters);
        intake.setUnitPrice(unitPrice);
        intake.setFee(fee);
        intake.setBalanceAfter(account.getBalance());
        intake.setOdorComplaint(odorComplaint);
        intakeRepository.save(intake);

        ChargeRecord charge = new ChargeRecord();
        charge.setChargeNo("CG" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        charge.setAccount(account);
        charge.setType(ChargeType.WATER_FEE);
        charge.setAmount(fee);
        charge.setStatus(ChargeStatus.SUCCESS);
        charge.setIntakeNo(intake.getIntakeNo());
        charge.setDescription(String.format("设备 %s 取水 %.1f 升 × %.2f 元/升", deviceNo, amountLiters, unitPrice));
        chargeRepository.save(charge);

        eventService.record(device, "INTAKE", intake.getIntakeNo(), String.format(
                "居民 %s 取水 %.1f 升，扣费 %.2f 元，余额 %.2f 元",
                account.getName(), amountLiters, fee, account.getBalance()));
        eventService.record(device, "CHARGE", charge.getChargeNo(),
                "取水扣费成功 " + fee + " 元（账户 " + accountNo + "）");

        try {
            producer.sendIntake(new IntakeMessage(intake.getIntakeNo(), deviceNo, accountNo,
                    device.getCommunity(), device.getBuilding(), amountLiters,
                    fee.toPlainString(), odorComplaint));
        } catch (Exception e) {
            log.warn("intake kafka send failed: {}", e.getMessage());
        }

        if (odorComplaint) {
            fileComplaint(device, account, intake.getIntakeNo(), "ODOR", "取水时发现异味");
        }
        return intake;
    }

    /** 登记投诉并评估投诉聚集 / 换芯后异味 */
    @Transactional
    public Complaint fileComplaint(Device device, ResidentAccount account, String intakeNo,
                                   String type, String content) {
        Complaint complaint = new Complaint();
        complaint.setDevice(device);
        complaint.setAccount(account);
        complaint.setIntakeNo(intakeNo);
        complaint.setType(type);
        complaint.setContent(content);
        complaintRepository.save(complaint);
        eventService.record(device, "COMPLAINT", null,
                "投诉[" + type + "] " + content + "（居民 " + account.getName() + "）");

        // 换芯后仍有异味：近 N 天内有换芯记录 → 专项预警 + 免费复检工单
        if ("ODOR".equals(type)) {
            replacementRepository.findTopByDeviceOrderByCompletedAtDesc(device).ifPresent(r -> {
                if (r.getCompletedAt().isAfter(LocalDateTime.now()
                        .minusDays(rules.complaint().postReplacementOdorDays()))) {
                    Alert alert = alertService.raise(device, AlertType.POST_REPLACEMENT_ODOR, "CRITICAL",
                            "换芯（工单 " + r.getWorkOrder().getOrderNo() + "）后 "
                                    + rules.complaint().postReplacementOdorDays() + " 天内仍有异味投诉");
                    workOrderService.createOrder(device, WorkOrderType.RECHECK, alert.getAlertNo(),
                            true, "换芯后异味免费复检");
                }
            });
        }

        // 投诉聚集：24 小时内达到阈值 → 预警 + 暂停售水 + 通知物业
        long count = complaintRepository.countByDeviceAndCreatedAtAfter(device,
                LocalDateTime.now().minusHours(rules.complaint().clusterWindowHours()));
        if (count >= rules.complaint().clusterThreshold()) {
            Alert alert = alertService.raise(device, AlertType.COMPLAINT_CLUSTER, "CRITICAL",
                    rules.complaint().clusterWindowHours() + " 小时内投诉 " + count
                            + " 起，达到聚集阈值，已暂停售水");
            device.setStatus(DeviceStatus.PAUSED);
            device.setPauseReason("水质投诉集中（" + count + " 起），已暂停售水，待复检合格后恢复");
            deviceRepository.save(device);
            eventService.record(device, "DEVICE_PAUSED", null, "投诉聚集暂停售水，共 " + count + " 起");
            notificationService.notify(NotificationService.PROPERTY,
                    device.getCommunity() + "/" + device.getBuilding(), "COMPLAINT_CLUSTER",
                    "设备 " + device.getDeviceNo() + " 投诉聚集（" + count + " 起）已暂停售水");
            workOrderService.createOrder(device, WorkOrderType.RECHECK, alert.getAlertNo(), true,
                    "投诉聚集水质复检");
        }
        return complaint;
    }
}
