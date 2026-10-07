package com.community.water.controller;

import com.community.water.dto.ApiDtos.DeviceRegisterRequest;
import com.community.water.entity.*;
import com.community.water.exception.BusinessException;
import com.community.water.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "设备管理", description = "设备注册、查询与履约链路")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final FilterCartridgeRepository filterRepository;
    private final DeviceEventRepository eventRepository;

    @Operation(summary = "注册设备（含首支滤芯）")
    @PostMapping
    public Device register(@Valid @RequestBody DeviceRegisterRequest req) {
        if (deviceRepository.findByDeviceNo(req.deviceNo()).isPresent()) {
            throw BusinessException.conflict("设备编号 " + req.deviceNo() + " 已存在");
        }
        Device device = new Device();
        device.setDeviceNo(req.deviceNo());
        device.setCommunity(req.community());
        device.setBuilding(req.building());
        device.setStatus(DeviceStatus.NORMAL);
        device.setInstalledAt(LocalDateTime.now());
        deviceRepository.save(device);

        FilterCartridge filter = new FilterCartridge();
        filter.setFilterNo(req.filterNo());
        filter.setBatchNo(req.batchNo());
        filter.setDevice(device);
        filter.setRatedCapacityLiters(req.ratedCapacityLiters());
        filterRepository.save(filter);
        return device;
    }

    @Operation(summary = "设备列表")
    @GetMapping
    public List<Device> list() {
        return deviceRepository.findAll();
    }

    @Operation(summary = "设备详情（含在用滤芯）")
    @GetMapping("/{deviceNo}")
    public Object detail(@PathVariable String deviceNo) {
        Device device = get(deviceNo);
        var filter = filterRepository.findByDeviceAndStatus(device, FilterStatus.ACTIVE).orElse(null);
        return java.util.Map.of("device", device, "activeFilter",
                filter == null ? "无在用滤芯" : filter);
    }

    @Operation(summary = "设备履约链路：设备/滤芯/取水/报修/换芯/收费/发票/复检统一时间线")
    @GetMapping("/{deviceNo}/chain")
    public List<DeviceEvent> chain(@PathVariable String deviceNo) {
        return eventRepository.findByDeviceOrderByCreatedAtAsc(get(deviceNo));
    }

    private Device get(String deviceNo) {
        return deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> BusinessException.notFound("设备 " + deviceNo));
    }
}
