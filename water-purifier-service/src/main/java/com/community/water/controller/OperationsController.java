package com.community.water.controller;

import com.community.water.entity.Alert;
import com.community.water.entity.AlertStatus;
import com.community.water.repository.AlertRepository;
import com.community.water.service.OperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "运营端", description = "按设备健康与投诉决定换芯策略")
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final OperationsService operationsService;
    private final AlertRepository alertRepository;

    @Operation(summary = "换芯策略：综合滤芯寿命、水质、投诉、取水强度与维护频率")
    @GetMapping("/replacement-strategy")
    public List<OperationsService.DeviceStrategy> strategy() {
        return operationsService.replacementStrategy();
    }

    @Operation(summary = "未解决预警列表")
    @GetMapping("/alerts/open")
    public List<Alert> openAlerts() {
        return alertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.OPEN);
    }
}
