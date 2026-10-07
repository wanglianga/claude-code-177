package com.community.water.core;

import com.community.water.config.AppProperties;
import com.community.water.domain.*;
import com.community.water.repo.Repositories.*;
import com.community.water.support.ApiException;
import com.community.water.web.dto.Requests.CreateDeviceRequest;
import com.community.water.web.dto.Requests.CreateFilterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 装配/档案管理：小区、设备（含策略阈值）、安装首个滤芯、师傅、居民账户。 */
@Service
public class SetupService {

    private final CommunityRepository communityRepository;
    private final DeviceRepository deviceRepository;
    private final WaterFilterRepository filterRepository;
    private final TechnicianRepository technicianRepository;
    private final ResidentAccountRepository accountRepository;
    private final AppProperties props;

    public SetupService(CommunityRepository communityRepository,
                        DeviceRepository deviceRepository,
                        WaterFilterRepository filterRepository,
                        TechnicianRepository technicianRepository,
                        ResidentAccountRepository accountRepository,
                        AppProperties props) {
        this.communityRepository = communityRepository;
        this.deviceRepository = deviceRepository;
        this.filterRepository = filterRepository;
        this.technicianRepository = technicianRepository;
        this.accountRepository = accountRepository;
        this.props = props;
    }

    @Transactional
    public Community createCommunity(String name, String address) {
        Community c = new Community();
        c.setName(name);
        c.setAddress(address);
        return communityRepository.save(c);
    }

    @Transactional
    public Device createDevice(CreateDeviceRequest req) {
        Community community = communityRepository.findById(req.communityId())
                .orElseThrow(() -> ApiException.badRequest("小区不存在: id=" + req.communityId()));
        if (deviceRepository.findByDeviceCode(req.deviceCode()).isPresent()) {
            throw ApiException.conflict("设备编号已存在: " + req.deviceCode());
        }
        Device d = new Device();
        d.setDeviceCode(req.deviceCode());
        d.setCommunityId(community.getId());
        d.setCommunityName(community.getName());
        d.setLocation(req.location());
        d.setBuilding(req.building());
        d.setStatus(DeviceStatus.ACTIVE);
        d.setLifeThresholdPercent(props.lifeThresholdPercent());
        d.setTdsLimit(props.tdsLimit());
        d.setChlorineLimit(props.chlorineLimit());
        d.setPricePerLiter(props.pricePerLiter());
        return deviceRepository.save(d);
    }

    /** 运营按设备健康/投诉动态调整换芯策略与水质阈值、水价。 */
    @Transactional
    public Device updateStrategy(String deviceCode, com.community.water.web.dto.Requests.DeviceStrategyRequest req) {
        Device d = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceCode));
        if (req.lifeThresholdPercent() != null) {
            d.setLifeThresholdPercent(req.lifeThresholdPercent());
        }
        if (req.tdsLimit() != null) {
            d.setTdsLimit(req.tdsLimit());
        }
        if (req.chlorineLimit() != null) {
            d.setChlorineLimit(req.chlorineLimit());
        }
        if (req.pricePerLiter() != null) {
            d.setPricePerLiter(req.pricePerLiter());
        }
        d.setUpdatedAt(OffsetDateTime.now());
        return deviceRepository.save(d);
    }

    @Transactional
    public WaterFilter installInitialFilter(Long deviceId, CreateFilterRequest req) {
        Device d = deviceRepository.findById(deviceId)
                .orElseThrow(() -> ApiException.notFound("设备不存在: id=" + deviceId));
        if (filterRepository.findByFilterSerialNo(req.filterSerialNo()).isPresent()) {
            throw ApiException.conflict("滤芯编号已存在: " + req.filterSerialNo());
        }
        WaterFilter f = new WaterFilter();
        f.setDeviceId(d.getId());
        f.setFilterSerialNo(req.filterSerialNo());
        f.setBatchNo(req.batchNo());
        f.setInstalledAt(OffsetDateTime.now());
        f.setExpectedCapacityLiters(req.expectedCapacityLiters());
        f.setLifePercent(100);
        f.setStatus(FilterStatus.IN_USE);
        filterRepository.save(f);
        d.setCurrentFilterId(f.getId());
        deviceRepository.save(d);
        return f;
    }

    @Transactional
    public Technician createTechnician(String name, String phone, Long communityId) {
        Technician t = new Technician();
        t.setName(name);
        t.setPhone(phone);
        t.setCommunityId(communityId);
        return technicianRepository.save(t);
    }

    @Transactional
    public ResidentAccount createAccount(String accountNo, String name, Long communityId,
                                         String building, String phone, BigDecimal initialBalance) {
        ResidentAccount a = new ResidentAccount();
        a.setAccountNo(accountNo);
        a.setResidentName(name);
        a.setCommunityId(communityId);
        a.setBuilding(building);
        a.setPhone(phone);
        a.setBalance(initialBalance == null ? BigDecimal.ZERO : initialBalance);
        return accountRepository.save(a);
    }

    @Transactional
    public ResidentAccount recharge(String accountNo, BigDecimal amount) {
        ResidentAccount a = accountRepository.findByAccountNo(accountNo)
                .orElseThrow(() -> ApiException.notFound("账户不存在: " + accountNo));
        a.setBalance(ChargeService.round2(a.getBalance().add(amount)));
        return accountRepository.save(a);
    }
}
