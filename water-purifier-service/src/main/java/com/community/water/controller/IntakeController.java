package com.community.water.controller;

import com.community.water.dto.ApiDtos.ComplaintRequest;
import com.community.water.dto.ApiDtos.IntakeRequest;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.*;
import com.community.water.service.IntakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "居民取水与投诉", description = "扫码取水扣费、异味投诉")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IntakeController {

    private final IntakeService intakeService;
    private final DeviceRepository deviceRepository;
    private final ResidentAccountRepository accountRepository;
    private final WaterIntakeRepository intakeRepository;
    private final ComplaintRepository complaintRepository;

    @Operation(summary = "居民扫码取水（校验设备状态与余额，扣费并记录；可附带异味投诉）")
    @PostMapping("/intakes")
    public WaterIntake intake(@Valid @RequestBody IntakeRequest req) {
        return intakeService.intake(req.deviceNo(), req.accountNo(),
                req.amountLiters(), req.odorComplaint());
    }

    @Operation(summary = "居民投诉（异味/水质；聚集时自动暂停售水）")
    @PostMapping("/complaints")
    public Complaint complaint(@Valid @RequestBody ComplaintRequest req) {
        Device device = deviceRepository.findByDeviceNo(req.deviceNo())
                .orElseThrow(() -> BusinessException.notFound("设备 " + req.deviceNo()));
        ResidentAccount account = accountRepository.findByAccountNo(req.accountNo())
                .orElseThrow(() -> BusinessException.notFound("账户 " + req.accountNo()));
        return intakeService.fileComplaint(device, account, null, req.type(), req.content());
    }

    @Operation(summary = "设备取水记录")
    @GetMapping("/devices/{deviceNo}/intakes")
    public List<WaterIntake> deviceIntakes(@PathVariable String deviceNo) {
        Device device = deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> BusinessException.notFound("设备 " + deviceNo));
        return intakeRepository.findByDeviceAndCreatedAtAfter(device,
                java.time.LocalDateTime.now().minusDays(90));
    }

    @Operation(summary = "设备投诉记录")
    @GetMapping("/devices/{deviceNo}/complaints")
    public List<Complaint> deviceComplaints(@PathVariable String deviceNo) {
        Device device = deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> BusinessException.notFound("设备 " + deviceNo));
        return complaintRepository.findByDeviceOrderByCreatedAtDesc(device);
    }
}
