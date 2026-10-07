package com.community.water.config;

import com.community.water.entity.*;
import com.community.water.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 演示数据：设备、滤芯、师傅、居民账户（幂等，已存在则跳过） */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final DeviceRepository deviceRepository;
    private final FilterCartridgeRepository filterRepository;
    private final TechnicianRepository technicianRepository;
    private final ResidentAccountRepository accountRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedTechnicians();
        seedDevices();
        seedAccounts();
    }

    private void seedTechnicians() {
        tech("T001", "张建国", "13800000001");
        tech("T002", "李卫国", "13800000002");
        tech("T003", "王志强", "13800000003");
    }

    private void tech(String techNo, String name, String phone) {
        if (technicianRepository.findByTechNo(techNo).isEmpty()) {
            Technician t = new Technician();
            t.setTechNo(techNo);
            t.setName(name);
            t.setPhone(phone);
            technicianRepository.save(t);
        }
    }

    private void seedDevices() {
        device("DEV-001", "阳光花园", "3栋", "FLT-001-A", "BATCH-2026-01", 10000, 100);
        device("DEV-002", "阳光花园", "5栋", "FLT-002-A", "BATCH-2026-01", 10000, 100);
        // DEV-003 滤芯接近阈值，便于演示预警与换芯流程
        device("DEV-003", "滨江新城", "1栋", "FLT-003-A", "BATCH-2025-11", 10000, 12);
    }

    private void device(String deviceNo, String community, String building,
                        String filterNo, String batchNo, double capacity, double lifePercent) {
        if (deviceRepository.findByDeviceNo(deviceNo).isPresent()) {
            return;
        }
        Device d = new Device();
        d.setDeviceNo(deviceNo);
        d.setCommunity(community);
        d.setBuilding(building);
        d.setStatus(DeviceStatus.NORMAL);
        d.setInstalledAt(LocalDateTime.now().minusDays(120));
        d.setLastMaintenanceAt(LocalDateTime.now().minusDays(30));
        deviceRepository.save(d);

        FilterCartridge f = new FilterCartridge();
        f.setFilterNo(filterNo);
        f.setBatchNo(batchNo);
        f.setDevice(d);
        f.setRatedCapacityLiters(capacity);
        f.setUsedLiters(capacity * (100 - lifePercent) / 100.0);
        f.setLifePercent(lifePercent);
        f.setInstalledAt(LocalDateTime.now().minusDays(60));
        filterRepository.save(f);
        log.info("seeded device {} with filter {} (life {}%)", deviceNo, filterNo, lifePercent);
    }

    private void seedAccounts() {
        account("ACC-1001", "陈小明", "阳光花园", "3栋", "301", "13900000001", "200.00");
        account("ACC-1002", "刘芳", "阳光花园", "3栋", "302", "13900000002", "50.00");
        account("ACC-1003", "王强", "阳光花园", "5栋", "501", "13900000003", "0.50");
        account("ACC-1004", "赵丽", "滨江新城", "1栋", "101", "13900000004", "300.00");
        account("ACC-1005", "孙杰", "滨江新城", "1栋", "102", "13900000005", "80.00");
    }

    private void account(String accountNo, String name, String community, String building,
                         String roomNo, String phone, String balance) {
        if (accountRepository.findByAccountNo(accountNo).isEmpty()) {
            ResidentAccount a = new ResidentAccount();
            a.setAccountNo(accountNo);
            a.setName(name);
            a.setCommunity(community);
            a.setBuilding(building);
            a.setRoomNo(roomNo);
            a.setPhone(phone);
            a.setBalance(new BigDecimal(balance));
            accountRepository.save(a);
        }
    }
}
