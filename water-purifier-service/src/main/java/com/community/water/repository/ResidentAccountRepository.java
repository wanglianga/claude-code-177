package com.community.water.repository;

import com.community.water.entity.ResidentAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResidentAccountRepository extends JpaRepository<ResidentAccount, Long> {
    Optional<ResidentAccount> findByAccountNo(String accountNo);

    List<ResidentAccount> findByCommunityAndBuilding(String community, String building);
}
