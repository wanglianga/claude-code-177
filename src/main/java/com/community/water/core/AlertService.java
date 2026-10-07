package com.community.water.core;

import com.community.water.domain.*;
import com.community.water.repo.Repositories.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** 预警服务：寿命、水质、投诉、迟到、换芯异味、扣费失败、监管抽查统一管理。 */
@Service
public class AlertService {

    private final AlertRepository repository;
    private final EventBus eventBus;

    public AlertService(AlertRepository repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    /**
     * 打开预警；同设备、同类型、同工单且未处理的预警去重。
     * 不同工单（如首次异味投诉与复检失败重挂单）各自留痕；扣费失败按收费记录区分。
     */
    @Transactional
    public Alert open(Device device, AlertType type, String message, FulfillmentCase c,
                      MaintenanceTicket ticket, Long chargeId, Long complaintId) {
        Long ticketId = ticket == null ? null : ticket.getId();
        boolean duplicate = repository.findByDeviceIdAndStatus(device.getId(), AlertStatus.OPEN).stream()
                .anyMatch(a -> a.getType() == type
                        && java.util.Objects.equals(a.getTicketId(), ticketId)
                        && (type != AlertType.PAYMENT_FAILED
                            || a.getChargeId() != null && a.getChargeId().equals(chargeId)));
        if (duplicate) {
            return repository.findByDeviceIdAndStatus(device.getId(), AlertStatus.OPEN).stream()
                    .filter(a -> a.getType() == type && java.util.Objects.equals(a.getTicketId(), ticketId))
                    .findFirst().orElseThrow();
        }
        Alert alert = new Alert();
        alert.setDeviceId(device.getId());
        alert.setCommunityId(device.getCommunityId());
        alert.setType(type);
        alert.setMessage(message);
        alert.setCaseId(c == null ? null : c.getId());
        alert.setTicketId(ticket == null ? null : ticket.getId());
        alert.setChargeId(chargeId);
        alert.setComplaintId(complaintId);
        return repository.save(alert);
    }

    /** 关闭设备上某类未处理预警（如换芯复检通过后）。 */
    @Transactional
    public int resolve(Device device, List<AlertType> types) {
        int n = 0;
        for (Alert a : repository.findByDeviceIdAndStatus(device.getId(), AlertStatus.OPEN)) {
            if (types.contains(a.getType())) {
                a.setStatus(AlertStatus.HANDLED);
                a.setHandledAt(OffsetDateTime.now());
                repository.save(a);
                n++;
            }
        }
        return n;
    }

    /** 扣费成功后精准关闭对应失败预警。 */
    @Transactional
    public void resolvePaymentAlert(Long chargeId) {
        repository.findAll().stream()
                .filter(a -> a.getStatus() == AlertStatus.OPEN
                        && a.getType() == AlertType.PAYMENT_FAILED
                        && chargeId.equals(a.getChargeId()))
                .forEach(a -> {
                    a.setStatus(AlertStatus.HANDLED);
                    a.setHandledAt(OffsetDateTime.now());
                    repository.save(a);
                });
    }
}
