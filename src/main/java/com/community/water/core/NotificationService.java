package com.community.water.core;

import com.community.water.domain.Device;
import com.community.water.domain.FulfillmentCase;
import com.community.water.domain.MaintenanceTicket;
import com.community.water.domain.Notification;
import com.community.water.repo.Repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知服务：暂停售水通知物业、派单通知师傅、费用/暂停解释触达居民。 */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Notification notify(Device device, String targetRole, String targetRef, String channel,
                               String title, String content, FulfillmentCase c, MaintenanceTicket t) {
        Notification n = new Notification();
        n.setDeviceId(device.getId());
        n.setCommunityId(device.getCommunityId());
        n.setTargetRole(targetRole);
        n.setTargetRef(targetRef);
        n.setChannel(channel);
        n.setTitle(title);
        n.setContent(content);
        n.setCaseId(c == null ? null : c.getId());
        n.setTicketId(t == null ? null : t.getId());
        return repository.save(n);
    }

    @Transactional
    public void notifyProperty(Device device, String title, String content, FulfillmentCase c, MaintenanceTicket t) {
        notify(device, "PROPERTY", null, "APP", title, content, c, t);
    }

    @Transactional
    public void notifyResidents(Device device, String title, String content, FulfillmentCase c) {
        notify(device, "RESIDENT", device.getBuilding(), "DISPLAY", title, content, c, null);
    }

    @Transactional
    public void notifyTechnician(Device device, String technicianName, String title, String content,
                                 FulfillmentCase c, MaintenanceTicket t) {
        notify(device, "TECHNICIAN", technicianName, "SMS", title, content, c, t);
    }
}
