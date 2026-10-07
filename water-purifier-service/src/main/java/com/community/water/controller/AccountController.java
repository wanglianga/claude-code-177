package com.community.water.controller;

import com.community.water.dto.ApiDtos.*;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "居民账户", description = "开户、充值、账单与收费解释")
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final ResidentAccountRepository accountRepository;
    private final ChargeRecordRepository chargeRepository;
    private final WaterIntakeRepository intakeRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceRepository deviceRepository;

    @Operation(summary = "开户")
    @PostMapping
    public ResidentAccount create(@Valid @RequestBody AccountCreateRequest req) {
        if (accountRepository.findByAccountNo(req.accountNo()).isPresent()) {
            throw BusinessException.conflict("账户号 " + req.accountNo() + " 已存在");
        }
        ResidentAccount account = new ResidentAccount();
        account.setAccountNo(req.accountNo());
        account.setName(req.name());
        account.setCommunity(req.community());
        account.setBuilding(req.building());
        account.setRoomNo(req.roomNo());
        account.setPhone(req.phone());
        account.setBalance(req.balance());
        return accountRepository.save(account);
    }

    @Operation(summary = "账户详情")
    @GetMapping("/{accountNo}")
    public ResidentAccount get(@PathVariable String accountNo) {
        return account(accountNo);
    }

    @Operation(summary = "充值")
    @PostMapping("/{accountNo}/recharge")
    public ResidentAccount recharge(@PathVariable String accountNo,
                                    @Valid @RequestBody RechargeRequest req) {
        ResidentAccount account = account(accountNo);
        account.setBalance(account.getBalance().add(req.amount()));
        return accountRepository.save(account);
    }

    @Operation(summary = "账户扣费记录")
    @GetMapping("/{accountNo}/charges")
    public List<ChargeRecord> charges(@PathVariable String accountNo) {
        return chargeRepository.findByAccountOrderByCreatedAtDesc(account(accountNo));
    }

    @Operation(summary = "账户取水记录")
    @GetMapping("/{accountNo}/intakes")
    public List<WaterIntake> intakes(@PathVariable String accountNo) {
        return intakeRepository.findByAccountOrderByCreatedAtDesc(account(accountNo));
    }

    @Operation(summary = "居民端解释：为什么暂停售水、为什么收费、每笔扣费说明")
    @GetMapping("/{accountNo}/explanations")
    public ResidentExplanation explanations(@PathVariable String accountNo) {
        ResidentAccount account = account(accountNo);
        // 同楼栋设备的暂停原因
        List<String> pauseReasons = deviceRepository.findAll().stream()
                .filter(d -> d.getCommunity().equals(account.getCommunity())
                        && d.getBuilding().equals(account.getBuilding())
                        && d.getStatus() != DeviceStatus.NORMAL)
                .map(d -> "设备 " + d.getDeviceNo() + "：" +
                        (d.getPauseReason() != null ? d.getPauseReason() : "维护中"))
                .toList();
        List<ChargeExplanation> charges = chargeRepository
                .findByAccountOrderByCreatedAtDesc(account).stream()
                .map(c -> new ChargeExplanation(c.getChargeNo(), c.getType().name(),
                        c.getAmount(), c.getStatus().name(), c.getDescription(),
                        c.getFailReason(), c.getCreatedAt().toString()))
                .toList();
        List<String> notices = notificationRepository
                .findByTargetTypeAndTargetRefOrderByCreatedAtDesc("RESIDENT", accountNo)
                .stream().map(n -> "[" + n.getCreatedAt() + "] " + n.getContent())
                .toList();
        return new ResidentExplanation(accountNo, account.getBalance(),
                pauseReasons, charges, notices);
    }

    private ResidentAccount account(String accountNo) {
        return accountRepository.findByAccountNo(accountNo)
                .orElseThrow(() -> BusinessException.notFound("账户 " + accountNo));
    }
}
