package com.community.water.service;

import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.ChargeRecordRepository;
import com.community.water.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 发票：开具与抬头错误更正重开 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ChargeRecordRepository chargeRepository;
    private final DeviceEventService eventService;

    @Transactional
    public Invoice issue(String chargeNo, String title, String taxNo) {
        ChargeRecord charge = chargeRepository.findByChargeNo(chargeNo)
                .orElseThrow(() -> BusinessException.notFound("收费记录 " + chargeNo));
        if (charge.getStatus() != ChargeStatus.SUCCESS) {
            throw BusinessException.conflict("收费记录 " + chargeNo + " 未成功扣款，不能开票");
        }
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo("INV" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        invoice.setCharge(charge);
        invoice.setTitle(title);
        invoice.setTaxNo(taxNo);
        invoice.setAmount(charge.getAmount());
        invoiceRepository.save(invoice);
        return invoice;
    }

    /** 发票抬头错误：作废原发票并按正确抬头重开 */
    @Transactional
    public Invoice correctTitle(String invoiceNo, String correctTitle, String correctTaxNo) {
        Invoice old = invoiceRepository.findByInvoiceNo(invoiceNo)
                .orElseThrow(() -> BusinessException.notFound("发票 " + invoiceNo));
        if (old.getStatus() != InvoiceStatus.ISSUED) {
            throw BusinessException.conflict("发票 " + invoiceNo + " 状态为 " + old.getStatus() + "，不能更正抬头");
        }
        old.setStatus(InvoiceStatus.TITLE_ERROR);
        invoiceRepository.save(old);

        Invoice reissued = new Invoice();
        reissued.setInvoiceNo("INV" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        reissued.setCharge(old.getCharge());
        reissued.setTitle(correctTitle);
        reissued.setTaxNo(correctTaxNo);
        reissued.setAmount(old.getAmount());
        reissued.setStatus(InvoiceStatus.REISSUED);
        reissued.setReissuedFrom(old.getInvoiceNo());
        invoiceRepository.save(reissued);
        return reissued;
    }

    @Transactional(readOnly = true)
    public Invoice get(String invoiceNo) {
        return invoiceRepository.findByInvoiceNo(invoiceNo)
                .orElseThrow(() -> BusinessException.notFound("发票 " + invoiceNo));
    }
}
