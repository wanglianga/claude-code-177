package com.community.water.controller;

import com.community.water.dto.ApiDtos.ArriveRequest;
import com.community.water.dto.ApiDtos.ReplacementCompleteRequest;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.*;
import com.community.water.service.ReplacementService;
import com.community.water.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工单与换芯", description = "工单调度、师傅到场、换芯履约确认")
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final ReplacementService replacementService;
    private final WorkOrderRepository workOrderRepository;
    private final FilterReplacementRepository replacementRepository;
    private final DeviceRepository deviceRepository;

    @Operation(summary = "工单列表（可按设备过滤）")
    @GetMapping
    public List<WorkOrder> list(@RequestParam(required = false) String deviceNo) {
        if (deviceNo != null) {
            Device device = deviceRepository.findByDeviceNo(deviceNo)
                    .orElseThrow(() -> BusinessException.notFound("设备 " + deviceNo));
            return workOrderService.listByDevice(device);
        }
        return workOrderRepository.findAll();
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{orderNo}")
    public WorkOrder detail(@PathVariable String orderNo) {
        return workOrderService.getOrder(orderNo);
    }

    @Operation(summary = "师傅到场确认")
    @PostMapping("/{orderNo}/arrive")
    public WorkOrder arrive(@PathVariable String orderNo, @Valid @RequestBody ArriveRequest req) {
        return workOrderService.arrive(orderNo, req.techNo());
    }

    @Operation(summary = "完成换芯：扫码确认旧滤芯编号、新滤芯批次、安装照片、冲洗时间、复检结果")
    @PostMapping("/{orderNo}/complete-replacement")
    public FilterReplacement completeReplacement(@PathVariable String orderNo,
                                                 @Valid @RequestBody ReplacementCompleteRequest req) {
        return replacementService.completeReplacement(orderNo, req.techNo(), req.oldFilterNo(),
                req.newFilterNo(), req.newBatchNo(), req.photoUrls(), req.flushMinutes(),
                req.recheckTds(), req.recheckChlorine());
    }

    @Operation(summary = "设备换芯历史")
    @GetMapping("/replacements")
    public List<FilterReplacement> replacements(@RequestParam String deviceNo) {
        Device device = deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> BusinessException.notFound("设备 " + deviceNo));
        return replacementRepository.findByDeviceOrderByCompletedAtDesc(device);
    }
}
