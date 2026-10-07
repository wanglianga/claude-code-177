package com.community.water.web;

import com.community.water.core.*;
import com.community.water.domain.DispenseRecord;
import com.community.water.domain.ResidentAccount;
import com.community.water.repo.Repositories.ResidentAccountRepository;
import com.community.water.support.ApiException;
import com.community.water.support.CurrentUsers;
import com.community.water.web.dto.Requests.ComplaintRequest;
import com.community.water.web.dto.Requests.DispenseRequest;
import com.community.water.web.dto.Requests.RechargeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 居民端：扫码取水、异味投诉、充值、扣费重试，并获知"为什么暂停/为什么收费"。 */
@RestController
@RequestMapping("/api/residents")
@Tag(name = "居民端", description = "取水、投诉、充值、收费解释（X-Role=RESIDENT，X-User-Id=账户号）")
public class ResidentController {

    private final DispenseService dispenseService;
    private final ComplaintService complaintService;
    private final SetupService setupService;
    private final ChargeService chargeService;
    private final ExplanationService explanationService;
    private final ResidentAccountRepository accountRepository;

    public ResidentController(DispenseService dispenseService, ComplaintService complaintService,
                              SetupService setupService, ChargeService chargeService,
                              ExplanationService explanationService,
                              ResidentAccountRepository accountRepository) {
        this.dispenseService = dispenseService;
        this.complaintService = complaintService;
        this.setupService = setupService;
        this.chargeService = chargeService;
        this.explanationService = explanationService;
        this.accountRepository = accountRepository;
    }

    @PostMapping("/dispenses")
    @Operation(summary = "扫码取水：按升扣费，记录余额；设备暂停时拒绝并说明原因")
    public DispenseRecord dispense(@Valid @RequestBody DispenseRequest req) {
        String accountNo = CurrentUsers.require().userId();
        if (!accountNo.equals(req.accountNo())) {
            throw new ApiException(403, "只能使用本人账户取水");
        }
        return dispenseService.dispense(req);
    }

    @GetMapping("/dispenses")
    @Operation(summary = "我的取水记录")
    public List<DispenseRecord> myDispenses() {
        return dispenseService.listForAccount(CurrentUsers.require().userId());
    }

    @PostMapping("/complaints")
    @Operation(summary = "登记异味/水质投诉（聚集到阈值将自动停机报修）")
    public Map<String, Object> complain(@RequestBody Map<String, String> body) {
        String accountNo = CurrentUsers.require().userId();
        String deviceCode = body.get("deviceCode");
        String content = body.getOrDefault("content", "取水有异味");
        Long dispenseId = parseLong(body.get("dispenseId"));
        var c = complaintService.registerResident(deviceCode, accountNo, content, dispenseId);
        return Map.of("complaintId", c.getId(), "status", c.getStatus().name());
    }

    @PostMapping("/recharge")
    @Operation(summary = "账户充值；充值后可在下一步重试历史失败扣费")
    public ResidentAccount recharge(@Valid @RequestBody RechargeRequest req) {
        return setupService.recharge(CurrentUsers.require().userId(), req.amount());
    }

    @PostMapping("/charges/retry-all")
    @Operation(summary = "充值后一键重试本人全部失败扣费（取水费/楼栋分摊）")
    public Map<String, Object> retryAll() {
        int n = chargeService.retryAllForAccount(CurrentUsers.require().userId());
        return Map.of("retried", n);
    }

    @GetMapping("/charges/{chargeNo}/explain")
    @Operation(summary = "为什么收费：计费公式、状态、发票与所属履约链路")
    public Map<String, Object> explainCharge(@PathVariable String chargeNo) {
        return explanationService.explainCharge(chargeNo, CurrentUsers.require().userId());
    }

    @GetMapping("/me")
    @Operation(summary = "我的账户信息（余额、楼栋）")
    public ResidentAccount me() {
        return accountRepository.findByAccountNo(CurrentUsers.require().userId())
                .orElseThrow(() -> ApiException.notFound("账户不存在"));
    }

    @GetMapping("/devices/{deviceCode}/explain")
    @Operation(summary = "设备为什么暂停/能否取水/如何收费")
    public Map<String, Object> explainDevice(@PathVariable String deviceCode) {
        return explanationService.explainDevice(deviceCode);
    }

    private Long parseLong(String v) {
        try {
            return v == null || v.isBlank() ? null : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
