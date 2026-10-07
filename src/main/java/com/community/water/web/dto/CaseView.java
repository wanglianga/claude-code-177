package com.community.water.web.dto;

import java.util.List;

/** 统一视图对象，按设备履约链路聚合全部环节，供三端查看。 */
public record CaseView(
        Long id,
        String caseNo,
        String deviceCode,
        Long communityId,
        String building,
        String primaryReason,
        String summary,
        String stage,
        boolean waterSuspended,
        boolean retestPassed,
        boolean billingSettled,
        boolean invoiceCorrect,
        DeviceInfo device,
        FilterInfo currentFilter,
        List<TelemetryInfo> recentTelemetry,
        List<DispenseInfo> recentDispenses,
        List<ComplaintInfo> complaints,
        List<TicketInfo> tickets,
        List<ReplacementInfo> replacements,
        List<RetestInfo> retests,
        List<ChargeInfo> charges,
        List<InvoiceInfo> invoices,
        List<AlertInfo> alerts,
        List<NotificationInfo> notifications,
        List<String> timeline) {

    public record DeviceInfo(Long id, String deviceCode, String status, String location, String building,
                             Integer lifeThresholdPercent, Integer tdsLimit, Double chlorineLimit,
                             String suspensionReason, Double cumulativeOutputLiters) {
    }

    public record FilterInfo(String filterSerialNo, String batchNo, Integer lifePercent, Double usedLiters,
                             Double expectedCapacityLiters, String status, boolean earlyExhausted,
                             String installedAt) {
    }

    public record TelemetryInfo(String eventTime, Integer filterLifePercent, Double waterOutputLiters,
                                Double tds, Double chlorine, Double flowRate, String faultCode,
                                Boolean qualityAbnormal) {
    }

    public record DispenseInfo(Long id, String dispensedAt, Double liters, String amount,
                               String balanceAfter, boolean chargeSucceeded, boolean smellComplaint,
                               String accountNo) {
    }

    public record ComplaintInfo(Long id, String accountNo, String content, String status, String createdAt) {
    }

    public record TicketInfo(Long id, String ticketNo, String trigger, String triggerDetail, String status,
                             String technicianName, String assignedAt, String dueAt, String arrivedAt,
                             boolean lateAlerted) {
    }

    public record ReplacementInfo(Long id, String technicianName, String oldFilterSerialNo,
                                  String newFilterSerialNo, String newFilterBatchNo, String installPhotoUrl,
                                  Integer flushMinutes, String replacedAt, String retestResult,
                                  String retestedAt, Double retestTds, Double retestChlorine) {
    }

    public record RetestInfo(String retestType, Double tds, Double chlorine, Double flowRate,
                             String result, String inspector, String createdAt, String note) {
    }

    public record ChargeInfo(Long id, String chargeNo, String type, String accountNo, String amount,
                             String status, int attempts, String reason, String failReason,
                             String invoiceNo) {
    }

    public record InvoiceInfo(Long id, String invoiceNo, String title, String taxNo, String amount,
                              String status, Long reissuedById) {
    }

    public record AlertInfo(Long id, String type, String message, String status, String createdAt,
                            Long ticketId) {
    }

    public record NotificationInfo(String targetRole, String channel, String title, String content,
                                   String createdAt, boolean delivered) {
    }
}
