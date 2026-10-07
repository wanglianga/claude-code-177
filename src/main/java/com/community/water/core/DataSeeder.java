package com.community.water.core;

import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 演示种子数据：1 个小区、2 台设备、2 位师傅、3 个楼栋账户。 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CommunityRepository communityRepository;
    private final SetupService setupService;
    private final WaterFilterRepository filterRepository;

    public DataSeeder(CommunityRepository communityRepository, SetupService setupService,
                      WaterFilterRepository filterRepository) {
        this.communityRepository = communityRepository;
        this.setupService = setupService;
        this.filterRepository = filterRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (communityRepository.count() > 0) {
            return;
        }
        Community c = setupService.createCommunity("阳光花园小区", "市中区花园路 88 号");

        Device d1 = setupService.createDevice(new com.community.water.web.dto.Requests.CreateDeviceRequest(
                "WQ-1001", c.getId(), "3 号楼大堂", "3号楼"));
        Device d2 = setupService.createDevice(new com.community.water.web.dto.Requests.CreateDeviceRequest(
                "WQ-1002", c.getId(), "5 号楼架空层", "5号楼"));

        setupService.installInitialFilter(d1.getId(),
                new com.community.water.web.dto.Requests.CreateFilterRequest(
                        "FLT-OLD-3-0001", "BATCH-2026-09", 10000.0));
        setupService.installInitialFilter(d2.getId(),
                new com.community.water.web.dto.Requests.CreateFilterRequest(
                        "FLT-OLD-5-0001", "BATCH-2026-09", 10000.0));

        setupService.createTechnician("王师傅", "13800000001", c.getId());
        setupService.createTechnician("李师傅", "13800000002", c.getId());

        setupService.createAccount("A3-101", "张三", c.getId(), "3号楼", "13900000001", new BigDecimal("50.00"));
        setupService.createAccount("A3-102", "李四", c.getId(), "3号楼", "13900000002", new BigDecimal("2.00"));
        setupService.createAccount("A5-201", "王五", c.getId(), "5号楼", "13900000003", new BigDecimal("30.00"));

        log.info("种子数据完成：小区 {}，设备 {}、{}", c.getName(), d1.getDeviceCode(), d2.getDeviceCode());
    }
}
