package com.community.water.controller;

import com.community.water.entity.Notification;
import com.community.water.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "物业端", description = "物业通知与费用解释")
@RestController
@RequestMapping("/api/property")
@RequiredArgsConstructor
public class PropertyController {

    private final NotificationRepository notificationRepository;

    @Operation(summary = "物业通知（停售/水质异常/投诉聚集/换芯完成）")
    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestParam String community,
                                            @RequestParam String building) {
        return notificationRepository.findByTargetTypeAndTargetRefOrderByCreatedAtDesc(
                "PROPERTY", community + "/" + building);
    }
}
