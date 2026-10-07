package com.community.water.repository;

import com.community.water.entity.Technician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {
    Optional<Technician> findByTechNo(String techNo);

    List<Technician> findByStatus(String status);
}
