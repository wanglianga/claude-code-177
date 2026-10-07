package com.community.water.core;

import com.community.water.config.AppProperties;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.Requests.ComplaintRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static com.community.water.config.KafkaTopicConfig.DEVICE_EVENT_TOPIC;

/**
 * 投诉服务：登记异味投诉；窗口内投诉达到聚集阈值则
 * 暂停售水、生成预警、报修派单；若发生在刚换芯之后，标记为"换芯后仍有异味"。
 */
@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final FilterReplacementRepository replacementRepository;
    private final CaseService caseService;
    private final DeviceService deviceService;
    private final TicketService ticketService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final EventBus eventBus;
    private final AppProperties props;

    public ComplaintService(ComplaintRepository complaintRepository,
                            FilterReplacementRepository replacementRepository,
                            CaseService caseService,
                            DeviceService deviceService,
                            TicketService ticketService,
                            AlertService alertService,
                            NotificationService notificationService,
                            EventBus eventBus,
                            AppProperties props) {
        this.complaintRepository = complaintRepository;
        this.replacementRepository = replacementRepository;
        this.caseService = caseService;
        this.deviceService = deviceService;
        this.ticketService = ticketService;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.eventBus = eventBus;
        this.props = props;
    }

    @Transactional
    public Complaint register(Device device, ResidentAccount account, String content, Long dispenseId) {
        Complaint c = new Complaint();
        c.setDeviceId(device.getId());
        c.setCommunityId(device.getCommunityId());
        c.setBuilding(device.getBuilding());
        c.setAccountNo(account.getAccountNo());
        c.setContent(content);
        c.setDispenseId(dispenseId);
        c.setStatus(ComplaintStatus.OPEN);
        complaintRepository.save(c);

        eventBus.emit(DEVICE_EVENT_TOPIC, EventType.COMPLAINT_RECEIVED, device.getDeviceCode(),
                EventBus.payload("deviceCode", device.getDeviceCode(), "accountNo", account.getAccountNo(),
                        "content", content, "complaintId", c.getId()));

        evaluateCluster(device, c);
        return c;
    }

    /** 居民自助登记异味/水质投诉。 */
    @Transactional
    public Complaint registerResident(String deviceCode, String accountNo, String content, Long dispenseId) {
        Device device = deviceService.requireByCode(deviceCode);
        ResidentAccount account = new ResidentAccount();
        account.setAccountNo(accountNo);
        return register(device, account, content, dispenseId);
    }

    /** 物业代居民登记投诉。 */
    @Transactional
    public Complaint registerByProperty(ComplaintRequest req) {
        Device device = deviceService.requireByCode(req.deviceCode());
        ResidentAccount account = new ResidentAccount();
        account.setAccountNo(req.accountNo());
        return register(device, account, req.content(), req.dispenseId());
    }

    private void evaluateCluster(Device device, Complaint latest) {
        OffsetDateTime since = OffsetDateTime.now().minusHours(props.complaintWindowHours());
        List<Complaint> recent = complaintRepository.findRecent(device.getId(), since);
        long openCount = recent.stream().filter(c -> c.getStatus() != ComplaintStatus.RESOLVED).count();

        // 最近 48h 是否有换芯：是则属"换芯后仍有异味"
        boolean afterReplacement = replacementRepository.findByDeviceIdOrderByReplacedAtDesc(device.getId()).stream()
                .findFirst()
                .map(r -> r.getReplacedAt() != null && r.getReplacedAt().isAfter(OffsetDateTime.now().minusHours(48)))
                .orElse(false);

        if (openCount < props.complaintClusterSize() && !afterReplacement) {
            return;
        }

        TicketTrigger trigger = afterReplacement
                ? TicketTrigger.SMELL_AFTER_REPLACE
                : TicketTrigger.COMPLAINT_CLUSTER;
        AlertType alertType = afterReplacement
                ? AlertType.SMELL_AFTER_REPLACE
                : AlertType.COMPLAINT_CLUSTER;
        String summary = (afterReplacement ? "换芯后 48h 内仍有异味投诉" :
                props.complaintWindowHours() + "h 内异味投诉达 " + openCount + " 起")
                + "，暂停售水并安排复查换芯";

        FulfillmentCase fc = caseService.obtainOpenCase(device, trigger.name(), summary);
        latest.setCaseId(fc.getId());
        complaintRepository.save(latest);
        recent.forEach(c -> {
            c.setCaseId(fc.getId());
            complaintRepository.save(c);
        });

        Alert alert = alertService.open(device, alertType, summary, fc, null, null, latest.getId());
        deviceService.suspend(device, "异味投诉聚集：" + summary, fc);
        MaintenanceTicket ticket = ticketService.openTicket(device, trigger, summary, fc);
        notificationService.notifyProperty(device, "【异味投诉处理】" + device.getDeviceCode(),
                summary + "，已生成工单 " + ticket.getTicketNo() + "。", fc, ticket);
        eventBus.emit(DEVICE_EVENT_TOPIC,
                afterReplacement ? EventType.RETEST_FAILED : EventType.COMPLAINT_CLUSTER,
                device.getDeviceCode(),
                EventBus.payload("deviceCode", device.getDeviceCode(), "complaintCount", openCount,
                        "afterReplacement", afterReplacement, "ticketNo", ticket.getTicketNo(),
                        "caseNo", fc.getCaseNo(), "alertId", alert.getId()));
    }

    @Transactional
    public Complaint resolve(Long complaintId) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("投诉不存在: id=" + complaintId));
        c.setStatus(ComplaintStatus.RESOLVED);
        c.setResolvedAt(OffsetDateTime.now());
        return complaintRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<Complaint> listForDevice(Long deviceId) {
        return complaintRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId);
    }
}
