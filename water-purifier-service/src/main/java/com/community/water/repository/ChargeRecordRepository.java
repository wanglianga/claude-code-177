package com.community.water.repository;

import com.community.water.entity.ChargeRecord;
import com.community.water.entity.ResidentAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChargeRecordRepository extends JpaRepository<ChargeRecord, Long> {
    Optional<ChargeRecord> findByChargeNo(String chargeNo);

    List<ChargeRecord> findByAccountOrderByCreatedAtDesc(ResidentAccount account);
}
