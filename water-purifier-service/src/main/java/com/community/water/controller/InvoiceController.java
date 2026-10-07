package com.community.water.controller;

import com.community.water.dto.ApiDtos.InvoiceCorrectRequest;
import com.community.water.dto.ApiDtos.InvoiceIssueRequest;
import com.community.water.entity.Invoice;
import com.community.water.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "发票", description = "开票与抬头错误更正")
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "按收费记录开票")
    @PostMapping
    public Invoice issue(@Valid @RequestBody InvoiceIssueRequest req) {
        return invoiceService.issue(req.chargeNo(), req.title(), req.taxNo());
    }

    @Operation(summary = "发票详情")
    @GetMapping("/{invoiceNo}")
    public Invoice get(@PathVariable String invoiceNo) {
        return invoiceService.get(invoiceNo);
    }

    @Operation(summary = "抬头错误更正：作废原发票并重开")
    @PostMapping("/{invoiceNo}/correct-title")
    public Invoice correctTitle(@PathVariable String invoiceNo,
                                @Valid @RequestBody InvoiceCorrectRequest req) {
        return invoiceService.correctTitle(invoiceNo, req.title(), req.taxNo());
    }
}
