package com.community.water.core;

/** 领域事件类型，随 Kafka / outbox 投递。 */
public final class EventType {

    public static final String TELEMETRY_RECEIVED = "TELEMETRY_RECEIVED";
    public static final String FILTER_LIFE_WARNING = "FILTER_LIFE_WARNING";
    public static final String FILTER_EARLY_EXHAUSTED = "FILTER_EARLY_EXHAUSTED";
    public static final String WATER_QUALITY_ABNORMAL = "WATER_QUALITY_ABNORMAL";
    public static final String DEVICE_SUSPENDED = "DEVICE_SUSPENDED";
    public static final String DEVICE_RESUMED = "DEVICE_RESUMED";
    public static final String DISPENSE_RECORDED = "DISPENSE_RECORDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_RETRIED = "PAYMENT_RETRIED";
    public static final String COMPLAINT_RECEIVED = "COMPLAINT_RECEIVED";
    public static final String COMPLAINT_CLUSTER = "COMPLAINT_CLUSTER";
    public static final String TICKET_CREATED = "TICKET_CREATED";
    public static final String TICKET_ASSIGNED = "TICKET_ASSIGNED";
    public static final String TECHNICIAN_LATE = "TECHNICIAN_LATE";
    public static final String FILTER_REPLACED = "FILTER_REPLACED";
    public static final String RETEST_PASSED = "RETEST_PASSED";
    public static final String RETEST_FAILED = "RETEST_FAILED";
    public static final String REGULATORY_AUDIT = "REGULATORY_AUDIT";
    public static final String MAINTENANCE_SHARED = "MAINTENANCE_SHARED";
    public static final String INVOICE_CORRECTED = "INVOICE_CORRECTED";
    public static final String CASE_OPENED = "CASE_OPENED";
    public static final String CASE_CLOSED = "CASE_CLOSED";

    private EventType() {
    }
}
