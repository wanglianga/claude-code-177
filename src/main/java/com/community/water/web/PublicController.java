package com.community.water.web;

import com.community.water.core.ExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 无需登录的公开接口：居民扫码即可看到"为什么暂停/如何收费"。 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "公开-扫码解释", description = "无需鉴权，居民扫码查看设备状态与暂停/收费解释")
public class PublicController {

    private final ExplanationService explanationService;

    public PublicController(ExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @GetMapping("/devices/{deviceCode}/explain")
    @Operation(summary = "设备解释：是否可取水、为什么暂停、何时恢复、如何收费")
    public Map<String, Object> explain(@PathVariable String deviceCode) {
        return explanationService.explainDevice(deviceCode);
    }
}
