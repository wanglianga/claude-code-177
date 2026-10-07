package com.community.water.core;

import com.community.water.domain.Device;
import com.community.water.domain.DeviceStatus;
import com.community.water.domain.FulfillmentCase;
import com.community.water.repo.Repositories.DeviceRepository;
import com.community.water.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;

/** 设备状态机：正常售水 ↔ 暂停售水 ↔ 维护中；恢复售水必须凭复检通过。 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final EventBus eventBus;
    private final NotificationService notificationService;

    public DeviceService(DeviceRepository deviceRepository, EventBus eventBus,
                         NotificationService notificationService) {
        this.deviceRepository = deviceRepository;
        this.eventBus = eventBus;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Device requireByCode(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceCode));
    }

    @Transactional(readOnly = true)
    public Device requireById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("设备不存在: id=" + id));
    }

    /** 水质异常暂停售水，并通知物业与居民"为什么暂停"。 */
    @Transactional
    public Device suspend(Device device, String reason, FulfillmentCase c) {
        if (device.getStatus() != DeviceStatus.SUSPENDED) {
            device.setStatus(DeviceStatus.SUSPENDED);
            device.setSuspensionReason(reason);
            device.setSuspendedAt(OffsetDateTime.now());
            device.setUpdatedAt(OffsetDateTime.now());
            deviceRepository.save(device);
            eventBus.emit(DEVICE_EVENT_TOPIC, EventType.DEVICE_SUSPENDED, device.getDeviceCode(),
                    EventBus.payload("deviceCode", device.getDeviceCode(), "reason", reason,
                            "caseNo", c == null ? null : c.getCaseNo()));
            notificationService.notifyProperty(device, "【售水暂停】" + device.getDeviceCode(),
                    reason + "。已暂停售水并安排处置。", c, null);
            notificationService.notifyResidents(device, "本机因水质安全暂停取水", reason, c);
            if (c != null) {
                c.setWaterSuspended(true);
            }
        }
        return device;
    }

    /** 进入维护（师傅到场换芯）。 */
    @Transactional
    public Device enterMaintenance(Device device) {
        device.setStatus(DeviceStatus.MAINTENANCE);
        device.setUpdatedAt(OffsetDateTime.now());
        return deviceRepository.save(device);
    }

    /** 复检通过后恢复售水。 */
    @Transactional
    public Device resume(Device device, String note, FulfillmentCase c) {
        device.setStatus(DeviceStatus.ACTIVE);
        device.setSuspensionReason(null);
        device.setSuspendedAt(null);
        device.setUpdatedAt(OffsetDateTime.now());
        deviceRepository.save(device);
        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.DEVICE_RESUMED, device.getDeviceCode(),
                EventBus.payload("deviceCode", device.getDeviceCode(), "note", note));
        notificationService.notifyProperty(device, "【恢复售水】" + device.getDeviceCode(),
                "换芯复检合格（TDS/余氯达标），已恢复售水。", c, null);
        notificationService.notifyResidents(device, "本机已恢复正常取水",
                "换芯完成且水质复检合格，感谢您的耐心等待。", c);
        return device;
    }

    @Transactional(readOnly = true)
    public boolean isSelling(Device device) {
        return device.getStatus() == DeviceStatus.ACTIVE;
    }
}
