package com.community.water.repository;

import com.community.water.entity.Device;
import com.community.water.entity.ResidentAccount;
import com.community.water.entity.WaterIntake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WaterIntakeRepository extends JpaRepository<WaterIntake, Long> {
    List<WaterIntake> findByDeviceAndCreatedAtAfter(Device device, LocalDateTime after);

    List<WaterIntake> findByAccountOrderByCreatedAtDesc(ResidentAccount account);
}
