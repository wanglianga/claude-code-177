package com.community.water.web.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Requests {

    public record CreateDeviceRequest(
            @NotBlank String deviceCode,
            @NotNull Long communityId,
            @NotBlank String location,
            @NotBlank String building) {
    }

    public record DeviceStrategyRequest(
            @Min(1) @Max(90) Integer lifeThresholdPercent,
            @Min(10) Integer tdsLimit,
            @Min(0) Double chlorineLimit,
            @DecimalMin("0.01") BigDecimal pricePerLiter) {
    }

    public record CreateFilterRequest(
            @NotBlank String filterSerialNo,
            @NotBlank String batchNo,
            @NotNull @Positive Double expectedCapacityLiters) {
    }

    public record TelemetryRequest(
            @NotBlank String deviceCode,
            OffsetDateTime eventTime,
            @NotNull @Min(0) @Max(100) Integer filterLifePercent,
            @NotNull @PositiveOrZero Double waterOutputLiters,
            @NotNull @PositiveOrZero Double tds,
            @NotNull @PositiveOrZero Double chlorine,
            @NotNull @PositiveOrZero Double flowRate,
            String faultCode,
            OffsetDateTime lastMaintenanceAt) {
    }

    public record DispenseRequest(
            @NotBlank String deviceCode,
            @NotBlank String accountNo,
            @NotNull @Positive Double liters,
            boolean smellComplaint,
            String complaintContent) {
    }

    public record ComplaintRequest(
            @NotBlank String deviceCode,
            @NotBlank String accountNo,
            @NotBlank String content,
            Long dispenseId) {
    }

    public record AssignRequest(
            @NotNull Long technicianId) {
    }

    public record ArriveRequest(
            @NotBlank String note) {
    }

    public record ReplaceFilterRequest(
            @NotBlank String ticketNo,
            @NotBlank String oldFilterSerialNo,
            @NotBlank String newFilterSerialNo,
            @NotBlank String newFilterBatchNo,
            @NotBlank String installPhotoUrl,
            @NotNull @Positive Integer flushMinutes,
            @NotNull @Positive Double newFilterCapacityLiters) {
    }

    public record RetestRequest(
            @NotBlank String ticketNo,
            @NotNull Double tds,
            @NotNull Double chlorine,
            @NotNull Double flowRate,
            String note) {
    }

    public record RegulatoryAuditRequest(
            @NotBlank String deviceCode,
            @NotNull Double tds,
            @NotNull Double chlorine,
            @NotNull Double flowRate,
            String note,
            @NotBlank String inspector) {
    }

    public record RetryChargeRequest(
            @NotBlank String chargeNo) {
    }

    public record MaintenanceShareRequest(
            @NotBlank String deviceCode,
            @NotNull @Positive BigDecimal totalAmount,
            String reason) {
    }

    public record CorrectInvoiceTitleRequest(
            @NotBlank String invoiceNo,
            @NotBlank String newTitle,
            @NotBlank String newTaxNo) {
    }

    public record ManualTicketRequest(
            @NotBlank String deviceCode,
            @NotBlank String reason) {
    }

    public record RechargeRequest(
            @NotNull @Positive BigDecimal amount) {
    }
}
