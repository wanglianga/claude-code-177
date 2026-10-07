package com.community.water.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** API 请求 DTO 集合 */
public final class ApiDtos {

    private ApiDtos() {}

    public record DeviceRegisterRequest(
            @NotBlank(message = "设备编号不能为空") String deviceNo,
            @NotBlank(message = "小区不能为空") String community,
            @NotBlank(message = "楼栋不能为空") String building,
            @NotBlank(message = "滤芯编号不能为空") String filterNo,
            @NotBlank(message = "滤芯批次不能为空") String batchNo,
            @Positive(message = "额定净水量必须为正") double ratedCapacityLiters
    ) {}

    public record TelemetryReportRequest(
            @NotBlank(message = "设备编号不能为空") String deviceNo,
            Double filterLifePercent,
            Double totalOutputLiters,
            Double tds,
            Double residualChlorine,
            Double flowRate,
            String faultCode,
            LocalDateTime lastMaintenanceAt
    ) {}

    public record IntakeRequest(
            @NotBlank(message = "设备编号不能为空") String deviceNo,
            @NotBlank(message = "账户号不能为空") String accountNo,
            @Positive(message = "取水量必须为正") double amountLiters,
            boolean odorComplaint
    ) {}

    public record ComplaintRequest(
            @NotBlank(message = "设备编号不能为空") String deviceNo,
            @NotBlank(message = "账户号不能为空") String accountNo,
            @NotBlank(message = "投诉类型不能为空") String type,
            @NotBlank(message = "投诉内容不能为空") String content
    ) {}

    public record AccountCreateRequest(
            @NotBlank(message = "账户号不能为空") String accountNo,
            @NotBlank(message = "姓名不能为空") String name,
            @NotBlank(message = "小区不能为空") String community,
            @NotBlank(message = "楼栋不能为空") String building,
            String roomNo,
            String phone,
            @NotNull(message = "初始余额不能为空") @DecimalMin("0") BigDecimal balance
    ) {}

    public record RechargeRequest(
            @NotNull(message = "充值金额不能为空") @Positive(message = "充值金额必须为正") BigDecimal amount
    ) {}

    public record TechnicianRegisterRequest(
            @NotBlank(message = "师傅工号不能为空") String techNo,
            @NotBlank(message = "姓名不能为空") String name,
            String phone
    ) {}

    public record ArriveRequest(
            @NotBlank(message = "师傅工号不能为空") String techNo
    ) {}

    public record ReplacementCompleteRequest(
            @NotBlank(message = "师傅工号不能为空") String techNo,
            @NotBlank(message = "旧滤芯编号不能为空") String oldFilterNo,
            @NotBlank(message = "新滤芯编号不能为空") String newFilterNo,
            @NotBlank(message = "新滤芯批次不能为空") String newBatchNo,
            @NotBlank(message = "安装照片不能为空") String photoUrls,
            @Min(value = 0, message = "冲洗时间不能为负") int flushMinutes,
            @NotNull(message = "复检 TDS 不能为空") Double recheckTds,
            @NotNull(message = "复检余氯不能为空") Double recheckChlorine
    ) {}

    public record InvoiceIssueRequest(
            @NotBlank(message = "收费单号不能为空") String chargeNo,
            @NotBlank(message = "发票抬头不能为空") String title,
            String taxNo
    ) {}

    public record InvoiceCorrectRequest(
            @NotBlank(message = "正确抬头不能为空") String title,
            String taxNo
    ) {}

    public record SpotCheckRequest(
            @NotBlank(message = "设备编号不能为空") String deviceNo,
            @NotNull(message = "TDS 不能为空") Double tds,
            @NotNull(message = "余氯不能为空") Double residualChlorine,
            @NotBlank(message = "检查人不能为空") String inspector,
            String note
    ) {}

    /** 居民端解释视图 */
    public record ResidentExplanation(
            String accountNo,
            BigDecimal balance,
            List<String> pauseReasons,
            List<ChargeExplanation> charges,
            List<String> notifications
    ) {}

    public record ChargeExplanation(
            String chargeNo,
            String type,
            BigDecimal amount,
            String status,
            String description,
            String failReason,
            String createdAt
    ) {}
}
