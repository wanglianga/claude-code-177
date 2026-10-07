package com.community.water.core;

import com.community.water.domain.Charge;
import com.community.water.domain.Invoice;
import com.community.water.domain.InvoiceStatus;
import com.community.water.domain.ResidentAccount;
import com.community.water.repo.Repositories.InvoiceRepository;
import com.community.water.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;

/**
 * 发票服务：收费成功即开票；抬头错误时冲红原发票（VOIDED）并重开（REISSUED），
 * 保留更正轨迹，保证费用解释可追溯。
 */
@Service
public class InvoiceService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InvoiceRepository invoiceRepository;
    private final EventBus eventBus;

    public InvoiceService(InvoiceRepository invoiceRepository, EventBus eventBus) {
        this.invoiceRepository = invoiceRepository;
        this.eventBus = eventBus;
    }

    private String newInvoiceNo() {
        return "INV-" + OffsetDateTime.now().format(NO_FMT) + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /** 个人发票默认抬头为居民姓名，税号留空。 */
    @Transactional
    public Invoice issueFor(Charge charge, ResidentAccount account) {
        return issue(charge, account.getResidentName(), "");
    }

    @Transactional
    public Invoice issue(Charge charge, String title, String taxNo) {
        Invoice inv = new Invoice();
        inv.setInvoiceNo(newInvoiceNo());
        inv.setChargeId(charge.getId());
        inv.setAccountNo(charge.getAccountNo());
        inv.setTitle(title);
        inv.setTaxNo(taxNo == null ? "" : taxNo);
        inv.setAmount(charge.getAmount());
        inv.setStatus(InvoiceStatus.ISSUED);
        return invoiceRepository.save(inv);
    }

    /** 抬头错误更正：原发票冲红，重开一张正确发票并相互关联。 */
    @Transactional
    public Invoice correctTitle(String invoiceNo, String newTitle, String newTaxNo) {
        Invoice old = invoiceRepository.findByInvoiceNo(invoiceNo)
                .orElseThrow(() -> ApiException.notFound("发票不存在: " + invoiceNo));
        if (old.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.conflict("发票状态 " + old.getStatus() + " 不允许更正（仅正常发票可冲红重开）");
        }
        old.setStatus(InvoiceStatus.VOIDED);
        invoiceRepository.save(old);

        Invoice neu = new Invoice();
        neu.setInvoiceNo(newInvoiceNo());
        neu.setChargeId(old.getChargeId());
        neu.setAccountNo(old.getAccountNo());
        neu.setTitle(newTitle);
        neu.setTaxNo(newTaxNo == null ? "" : newTaxNo);
        neu.setAmount(old.getAmount());
        neu.setStatus(InvoiceStatus.REISSUED);
        neu.setReissuedFromId(old.getId());
        invoiceRepository.save(neu);

        old.setReissuedById(neu.getId());
        invoiceRepository.save(old);

        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.INVOICE_CORRECTED, neu.getInvoiceNo(),
                EventBus.payload("voidedInvoiceNo", old.getInvoiceNo(),
                        "newInvoiceNo", neu.getInvoiceNo(), "newTitle", newTitle));
        return neu;
    }

    @Transactional(readOnly = true)
    public Invoice require(String invoiceNo) {
        return invoiceRepository.findByInvoiceNo(invoiceNo)
                .orElseThrow(() -> ApiException.notFound("发票不存在: " + invoiceNo));
    }
}
