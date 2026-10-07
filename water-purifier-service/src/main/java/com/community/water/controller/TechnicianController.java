package com.community.water.controller;

import com.community.water.dto.ApiDtos.TechnicianRegisterRequest;
import com.community.water.entity.Technician;
import com.community.water.exception.BusinessException;
import com.community.water.repository.TechnicianRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "师傅管理", description = "维护师傅注册与查询")
@RestController
@RequestMapping("/api/technicians")
@RequiredArgsConstructor
public class TechnicianController {

    private final TechnicianRepository technicianRepository;

    @Operation(summary = "注册师傅")
    @PostMapping
    public Technician register(@Valid @RequestBody TechnicianRegisterRequest req) {
        if (technicianRepository.findByTechNo(req.techNo()).isPresent()) {
            throw BusinessException.conflict("师傅工号 " + req.techNo() + " 已存在");
        }
        Technician tech = new Technician();
        tech.setTechNo(req.techNo());
        tech.setName(req.name());
        tech.setPhone(req.phone());
        return technicianRepository.save(tech);
    }

    @Operation(summary = "师傅列表")
    @GetMapping
    public List<Technician> list() {
        return technicianRepository.findAll();
    }
}
