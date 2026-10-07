package com.community.water.service;

import com.community.water.entity.Device;
import com.community.water.entity.DeviceEvent;
import com.community.water.repository.DeviceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 设备履约链路事件记录 */
@Service
@RequiredArgsConstructor
public class DeviceEventService {

    private final DeviceEventRepository eventRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Device device, String eventType, String refNo, String summary) {
        DeviceEvent event = new DeviceEvent();
        event.setDevice(device);
        event.setEventType(eventType);
        event.setRefNo(refNo);
        event.setSummary(summary);
        eventRepository.save(event);
    }
}
