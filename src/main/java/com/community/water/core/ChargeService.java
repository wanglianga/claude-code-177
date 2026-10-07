package com.community.water.core;

import com.community.water.domain.*;
import com.community.water.repo.Repositories.ChargeRepository;
import com.community.water.repo.Repositories.DeviceRepository;
import com.community.water.repo.Repositories.ResidentAccountRepository;
import com.community.water.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;

/**
 * 收费服务：居民取水扣费（可能余额不足失败、可重试）与楼栋维护费分摊。
 * 扣费失败产生预警并挂入设备履约链路；成功后自动开具发票。
 */
@Service
public class ChargeService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ChargeRepository chargeRepository;
    private final ResidentAccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final CaseService caseService;
    private final InvoiceService invoiceService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final EventBus eventBus;

    public ChargeService(ChargeRepository chargeRepository,
                         ResidentAccountRepository accountRepository,
                         DeviceRepository deviceRepository,
                         CaseService caseService,
                         InvoiceService invoiceService,
                         AlertService alertService,
                         NotificationService notificationService,
                         EventBus eventBus) {
        this.chargeRepository = chargeRepository;
        this.accountRepository = accountRepository;
        this.deviceRepository = deviceRepository;
        this.caseService = caseService;
        this.invoiceService = invoiceService;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.eventBus = eventBus;
    }

    public static BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private String newChargeNo() {
        return "CHG-" + OffsetDateTime.now().format(NO_FMT) + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /** 尝试一次扣费；余额不足时落 FAILED 记录并告警，不吞异常。 */
    @Transactional
    public Charge attempt(ResidentAccount account, Device device, BigDecimal amount, ChargeType type,
                          String reason, Long dispenseId, Long ticketId, FulfillmentCase c) {
        Charge charge = new Charge();
        charge.setChargeNo(newChargeNo());
        charge.setDeviceId(device.getId());
        charge.setCommunityId(device.getCommunityId());
        charge.setBuilding(device.getBuilding());
        charge.setAccountNo(account.getAccountNo());
        charge.setType(type);
        charge.setAmount(round2(amount));
        charge.setReason(reason);
        charge.setDispenseId(dispenseId);
        charge.setTicketId(ticketId);
        charge.setCaseId(c == null ? null : c.getId());
        charge.setStatus(ChargeStatus.PENDING);
        charge.setAttempts(1);
        chargeRepository.save(charge);
        applyDeduction(charge, account, device, c);
        return chargeRepository.save(charge);
    }

    /** 扣费结果落账：成功扣款+开票；失败告警。 */
    private void applyDeduction(Charge charge, ResidentAccount account, Device device, FulfillmentCase c) {
        if (account.getBalance().compareTo(charge.getAmount()) >= 0) {
            account.setBalance(round2(account.getBalance().subtract(charge.getAmount())));
            accountRepository.save(account);
            charge.setStatus(ChargeStatus.SUCCESS);
            charge.setPaidAt(OffsetDateTime.now());
            Invoice inv = invoiceService.issueFor(charge, account);
            charge.setInvoiceId(inv.getId());
        } else {
            charge.setStatus(ChargeStatus.FAILED);
            charge.setFailReason("账户余额不足：余额 " + account.getBalance() + "，应付 " + charge.getAmount());
            Alert alert = alertService.open(device, AlertType.PAYMENT_FAILED,
                    "账户 " + account.getAccountNo() + " " + charge.getFailReason(),
                    c, null, charge.getId(), null);
            notificationService.notifyProperty(device, "【扣费失败】" + device.getDeviceCode(),
                    "账户 " + account.getAccountNo() + " 扣费失败（" + charge.getType()
                            + "）：" + charge.getFailReason(), c, null);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.PAYMENT_FAILED, charge.getChargeNo(),
                    EventBus.payload("chargeNo", charge.getChargeNo(), "accountNo", account.getAccountNo(),
                            "amount", charge.getAmount().toString(), "alertId", alert.getId(),
                            "caseNo", c == null ? null : c.getCaseNo()));
        }
    }

    /** 失败扣费重试：居民充值后重新扣款，成功则关闭原告警。 */
    @Transactional
    public Charge retry(String chargeNo) {
        Charge charge = chargeRepository.findByChargeNo(chargeNo)
                .orElseThrow(() -> ApiException.notFound("收费记录不存在: " + chargeNo));
        if (charge.getStatus() == ChargeStatus.SUCCESS) {
            throw ApiException.conflict("收费记录已成功，无需重试");
        }
        ResidentAccount account = accountRepository.findByAccountNo(charge.getAccountNo())
                .orElseThrow(() -> ApiException.notFound("账户不存在: " + charge.getAccountNo()));
        charge.setAttempts(charge.getAttempts() + 1);
        Device device = deviceRepository.findById(charge.getDeviceId())
                .orElseGet(() -> {
                    Device stub = new Device();
                    stub.setId(0L);
                    stub.setDeviceCode(charge.getChargeNo());
                    stub.setCommunityId(charge.getCommunityId());
                    stub.setBuilding(charge.getBuilding());
                    return stub;
                });
        FulfillmentCase c = charge.getCaseId() == null ? caseService.findLatestCase(device.getId()) : null;
        if (account.getBalance().compareTo(charge.getAmount()) >= 0) {
            account.setBalance(round2(account.getBalance().subtract(charge.getAmount())));
            accountRepository.save(account);
            charge.setStatus(ChargeStatus.SUCCESS);
            charge.setFailReason(null);
            charge.setPaidAt(OffsetDateTime.now());
            Invoice inv = invoiceService.issueFor(charge, account);
            charge.setInvoiceId(inv.getId());
            alertService.resolvePaymentAlert(charge.getId());
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.PAYMENT_RETRIED, charge.getChargeNo(),
                    EventBus.payload("chargeNo", charge.getChargeNo(), "accountNo", account.getAccountNo(),
                            "attempts", charge.getAttempts(),
                            "caseNo", c == null ? null : c.getCaseNo()));
        } else {
            charge.setStatus(ChargeStatus.FAILED);
            charge.setFailReason("重试仍余额不足：余额 " + account.getBalance() + "，应付 " + charge.getAmount());
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.PAYMENT_FAILED, charge.getChargeNo(),
                    EventBus.payload("chargeNo", charge.getChargeNo(), "attempts", charge.getAttempts()));
        }
        return chargeRepository.save(charge);
    }

    /** 充值后自动重试该账户全部失败扣费。 */
    @Transactional
    public int retryAllForAccount(String accountNo) {
        List<Charge> failed = chargeRepository.findByAccountNoOrderByCreatedAtDesc(accountNo).stream()
                .filter(ch -> ch.getStatus() == ChargeStatus.FAILED)
                .toList();
        for (Charge ch : failed) {
            retry(ch.getChargeNo());
        }
        return failed.size();
    }

    @Transactional(readOnly = true)
    public Charge require(String chargeNo) {
        return chargeRepository.findByChargeNo(chargeNo)
                .orElseThrow(() -> ApiException.notFound("收费记录不存在: " + chargeNo));
    }

    /**
     * 物业要求按楼栋分摊维护费：把总金额均摊到该小区该楼栋全部居民账户，
     * 逐户扣款开票；余额不足的户落 FAILED 并告警，链路 billingSettled 需等其结清。
     * 返回每户收费记录。
     */
    @Transactional
    public java.util.List<Charge> shareByBuilding(Device device, BigDecimal totalAmount,
                                                  String reason, FulfillmentCase c) {
        java.util.List<ResidentAccount> accounts =
                accountRepository.findByCommunityIdAndBuilding(device.getCommunityId(), device.getBuilding());
        if (accounts.isEmpty()) {
            throw ApiException.badRequest("该楼栋暂无居民账户，无法分摊：" + device.getBuilding());
        }
        int n = accounts.size();
        BigDecimal each = round2(totalAmount.divide(BigDecimal.valueOf(n), RoundingMode.DOWN));
        BigDecimal distributed = BigDecimal.ZERO;
        java.util.List<Charge> charges = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            ResidentAccount account = accounts.get(i);
            // 尾差并入最后一户，保证分摊总额一致（便于物业向居民解释）
            BigDecimal amount = (i == n - 1) ? round2(totalAmount.subtract(distributed)) : each;
            distributed = round2(distributed.add(amount));
            Charge charge = attempt(account, device, amount, ChargeType.MAINTENANCE_SHARE,
                    (reason == null ? "楼栋换芯维护费分摊" : reason) + "（" + device.getBuilding()
                            + " 共 " + n + " 户分摊）", null, null, c);
            charges.add(charge);
        }
        long failed = charges.stream().filter(ch -> ch.getStatus() == ChargeStatus.FAILED).count();
        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.MAINTENANCE_SHARED, device.getDeviceCode(),
                EventBus.payload("deviceCode", device.getDeviceCode(), "building", device.getBuilding(),
                        "totalAmount", totalAmount.toString(), "households", n, "failed", failed,
                        "caseNo", c == null ? null : c.getCaseNo()));
        return charges;
    }
}
