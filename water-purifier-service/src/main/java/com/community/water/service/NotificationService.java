package com.community.water.service;

import com.community.water.entity.Notification;
import com.community.water.kafka.KafkaProducerService;
import com.community.water.kafka.msg.NotificationMessage;
import com.community.water.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 通知：落库 + 发 Kafka，供短信/App 推送等下游消费 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String PROPERTY = "PROPERTY";
    public static final String RESIDENT = "RESIDENT";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String OPERATIONS = "OPERATIONS";

    private final NotificationRepository notificationRepository;
    private final KafkaProducerService producer;

    @Transactional(propagation = Propagation.REQUIRED)
    public void notify(String targetType, String targetRef, String type, String content) {
        Notification n = new Notification();
        n.setTargetType(targetType);
        n.setTargetRef(targetRef);
        n.setType(type);
        n.setContent(content);
        notificationRepository.save(n);
        try {
            producer.sendNotification(new NotificationMessage(targetType, targetRef, type, content));
        } catch (Exception e) {
            log.warn("notification kafka send failed: {}", e.getMessage());
        }
    }
}
