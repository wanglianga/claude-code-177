package com.community.water;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** REST API 层测试：参数校验、错误语义、居民解释端点、OpenAPI 文档可用性 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// 独立的内存库，避免与集成测试共享 H2 实例互相清表
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:waterdb_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@EmbeddedKafka(partitions = 1, topics = {
        "water.device.telemetry", "water.device.intake", "water.alert", "water.notification"})
class ApiLayerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void intakeValidationAndNotFound() throws Exception {
        // 缺少必填字段 → 400
        mvc.perform(post("/api/intakes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceNo\":\"DEV-001\"}"))
                .andExpect(status().isBadRequest());
        // 设备不存在 → 404
        mvc.perform(post("/api/intakes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceNo\":\"NOPE\",\"accountNo\":\"ACC-1001\",\"amountLiters\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("NOPE")));
    }

    @Test
    void residentExplanationsEndpoint() throws Exception {
        mvc.perform(get("/api/accounts/ACC-1001/explanations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNo").value("ACC-1001"))
                .andExpect(jsonPath("$.balance").isNumber())
                .andExpect(jsonPath("$.pauseReasons").isArray())
                .andExpect(jsonPath("$.charges").isArray())
                .andExpect(jsonPath("$.notifications").isArray());
    }

    @Test
    void deviceChainAndStrategyEndpoints() throws Exception {
        mvc.perform(get("/api/devices/DEV-001/chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/operations/replacement-strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].recommendedAction", notNullValue()));
        mvc.perform(get("/api/devices/NOPE")).andExpect(status().isNotFound());
    }

    @Test
    void openApiDocsAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", containsString("净水")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
