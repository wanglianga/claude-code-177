package com.community.water.entity;

public enum InvoiceStatus {
    ISSUED,
    /** 抬头错误，已作废待重开 */
    TITLE_ERROR,
    REISSUED
}
