package com.community.water.repository;

import com.community.water.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByTargetTypeAndTargetRefOrderByCreatedAtDesc(String targetType, String targetRef);
}
