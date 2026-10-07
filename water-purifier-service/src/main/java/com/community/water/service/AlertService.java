package com.community.water.service;

import com.community.water.entity.*;
import com.community.water.kafka.KafkaProducerService;
import com.community.water.kafka.msg.AlertMessage;
import com.community.water.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final KafkaProducerService producer;
    private final DeviceEventService eventService;
    private final NotificationService notificationService;

    /**
     * 创建预警（同设备同类型存在未解决预警时不重复创建）。
     *
     * @return 新建或复用的预警
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Alert raise(Device device, AlertType type, String level, String message) {
        Alert existing = alertRepository.findByDeviceOrderByCreatedAtDesc(device).stream()
                .filter(a -> a.getType() == type && a.getStatus() != AlertStatus.RESOLVED)
                .findFirst().orElse(null);
        if (existing != null) {
            log.info("alert dedup: device={} type={} reuse {}", device.getDeviceNo(), type, existing.getAlertNo());
            return existing;
        }
        Alert alert = new Alert();
        alert.setAlertNo("AL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        alert.setDevice(device);
        alert.setType(type);
        alert.setLevel(level);
        alert.setMessage(message);
        alertRepository.save(alert);

        eventService.record(device, "ALERT", alert.getAlertNo(), "预警[" + type + "] " + message);
        try {
            producer.sendAlert(new AlertMessage(alert.getAlertNo(), device.getDeviceNo(),
                    type.name(), level, message));
        } catch (Exception e) {
            log.warn("alert kafka send failed: {}", e.getMessage());
        }
        // 运营端始终可见
        notificationService.notify(NotificationService.OPERATIONS, "OPS",
                type.name(), "设备 " + device.getDeviceNo() + " 预警: " + message);
        return alert;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void resolve(Device device, AlertType type, String reason) {
        List<Alert> open = alertRepository.findByDeviceOrderByCreatedAtDesc(device).stream()
                .filter(a -> a.getType() == type && a.getStatus() != AlertStatus.RESOLVED)
                .toList();
        for (Alert a : open) {
            a.setStatus(AlertStatus.RESOLVED);
            a.setResolvedAt(LocalDateTime.now());
            alertRepository.save(a);
            eventService.record(device, "ALERT", a.getAlertNo(), "预警解除[" + type + "] " + reason);
        }
    }
}
