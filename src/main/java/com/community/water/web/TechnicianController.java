package com.community.water.web;

import com.community.water.core.*;
import com.community.water.domain.FilterReplacement;
import com.community.water.domain.MaintenanceTicket;
import com.community.water.domain.Technician;
import com.community.water.domain.WaterQualityRetest;
import com.community.water.repo.Repositories.MaintenanceTicketRepository;
import com.community.water.repo.Repositories.TechnicianRepository;
import com.community.water.support.ApiException;
import com.community.water.support.CurrentUsers;
import com.community.water.web.dto.Requests.ArriveRequest;
import com.community.water.web.dto.Requests.ReplaceFilterRequest;
import com.community.water.web.dto.Requests.RetestRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 师傅端：查看派单、到场确认、扫码换芯（旧芯/新批次/照片/冲洗）、提交复检。 */
@RestController
@RequestMapping("/api/technician")
@Tag(name = "师傅端", description = "到场、扫码换芯、复检（X-Role=TECHNICIAN，X-User-Id=师傅ID）")
public class TechnicianController {

    private final TicketService ticketService;
    private final MaintenanceExecutionService executionService;
    private final TechnicianRepository technicianRepository;
    private final MaintenanceTicketRepository ticketRepository;

    public TechnicianController(TicketService ticketService, MaintenanceExecutionService executionService,
                                TechnicianRepository technicianRepository,
                                MaintenanceTicketRepository ticketRepository) {
        this.ticketService = ticketService;
        this.executionService = executionService;
        this.technicianRepository = technicianRepository;
        this.ticketRepository = ticketRepository;
    }

    private Long techId() {
        try {
            return Long.parseLong(CurrentUsers.require().userId());
        } catch (NumberFormatException e) {
            throw new ApiException(401, "师傅 X-User-Id 必须为数字 ID");
        }
    }

    private String techName() {
        return technicianRepository.findById(techId()).map(Technician::getName).orElse("未知师傅");
    }

    @GetMapping("/tickets")
    @Operation(summary = "派给我的工单")
    public List<MaintenanceTicket> myTickets() {
        return ticketRepository.findByTechnicianIdOrderByCreatedAtDesc(techId());
    }

    @PostMapping("/tickets/{ticketNo}/arrive")
    @Operation(summary = "到场确认（超过 SLA 的工单会被标记迟到）")
    public MaintenanceTicket arrive(@PathVariable String ticketNo, @Valid @RequestBody ArriveRequest req) {
        MaintenanceTicket t = ticketService.require(ticketNo);
        if (!techId().equals(t.getTechnicianId())) {
            throw new ApiException(403, "工单未指派给当前师傅");
        }
        return ticketService.arrive(ticketNo, req.note());
    }

    @PostMapping("/replacements")
    @Operation(summary = "扫码换芯：旧滤芯编号、新滤芯批次、安装照片、冲洗时间")
    public FilterReplacement replace(@Valid @RequestBody ReplaceFilterRequest req) {
        return executionService.replaceFilter(req, techId());
    }

    @PostMapping("/retests")
    @Operation(summary = "换芯后提交水质复检；不合格继续停机并重新挂单")
    public WaterQualityRetest retest(@Valid @RequestBody RetestRequest req) {
        return executionService.retest(req, techName());
    }
}
