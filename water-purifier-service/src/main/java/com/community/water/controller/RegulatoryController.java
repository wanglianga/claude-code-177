package com.community.water.controller;

import com.community.water.dto.ApiDtos.SpotCheckRequest;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.*;
import com.community.water.service.DeviceEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "监管端", description = "监管抽查水质记录")
@RestController
@RequestMapping("/api/regulatory")
@RequiredArgsConstructor
public class RegulatoryController {

    private final DeviceRepository deviceRepository;
    private final DeviceTelemetryRepository telemetryRepository;
    private final RecheckRecordRepository recheckRepository;
    private final DeviceEventService eventService;

    @Operation(summary = "监管抽查：设备水质记录（遥测 + 复检）")
    @GetMapping("/water-quality/{deviceNo}")
    public Map<String, Object> waterQuality(
            @PathVariable String deviceNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Device device = deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> BusinessException.notFound("设备 " + deviceNo));
        LocalDateTime f = from != null ? from : LocalDateTime.now().minusDays(30);
        LocalDateTime t = to != null ? to : LocalDateTime.now();
        List<DeviceTelemetry> telemetry = telemetryRepository
                .findByDeviceAndReportedAtBetweenOrderByReportedAt(device, f, t);
        List<RecheckRecord> rechecks = recheckRepository
                .findByDeviceAndCreatedAtBetweenOrderByCreatedAt(device, f, t);
        return Map.of("deviceNo", deviceNo, "from", f.toString(), "to", t.toString(),
                "telemetry", telemetry, "rechecks", rechecks);
    }

    @Operation(summary = "监管抽查登记：现场水质检测结果留痕")
    @PostMapping("/spot-check")
    public RecheckRecord spotCheck(@Valid @RequestBody SpotCheckRequest req) {
        Device device = deviceRepository.findByDeviceNo(req.deviceNo())
                .orElseThrow(() -> BusinessException.notFound("设备 " + req.deviceNo()));
        RecheckRecord record = new RecheckRecord();
        record.setDevice(device);
        record.setKind("REGULATORY");
        record.setTds(req.tds());
        record.setResidualChlorine(req.residualChlorine());
        boolean pass = req.tds() <= 100 && req.residualChlorine() >= 0.05 && req.residualChlorine() <= 2.0;
        record.setResult(pass ? RecheckResult.PASS : RecheckResult.FAIL);
        record.setInspector(req.inspector());
        record.setNote(req.note());
        recheckRepository.save(record);
        eventService.record(device, "RECHECK", null, String.format(
                "监管抽查：TDS=%.1f，余氯=%.2f，结果=%s（检查人 %s）",
                req.tds(), req.residualChlorine(), pass ? "合格" : "不合格", req.inspector()));
        return record;
    }
}
