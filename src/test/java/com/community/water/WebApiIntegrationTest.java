package com.community.water;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 三端 REST API、角色鉴权与公开解释接口测试（使用种子数据：小区 1 / 设备 WQ-1001、WQ-1002 / 账户 A3-101 等）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void publicExplain_doesNotRequireAuth() throws Exception {
        mockMvc.perform(get("/api/public/devices/WQ-1001/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceCode").value("WQ-1001"))
                .andExpect(jsonPath("$.selling").value(true));
    }

    @Test
    void missingAuthHeaders_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/residents/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/ops/devices").header("X-Role", "RESIDENT").header("X-User-Id", "A3-101"))
                .andExpect(status().isForbidden());
    }

    @Test
    void residentDispense_successAndPaymentFailureBothRecorded() throws Exception {
        // 余额充足账户 A3-101 取水 10L = 3.00 元
        mockMvc.perform(post("/api/residents/dispenses")
                        .header("X-Role", "RESIDENT").header("X-User-Id", "A3-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceCode", "WQ-1001", "accountNo", "A3-101", "liters", 10.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeSucceeded").value(true))
                .andExpect(jsonPath("$.amount").value(3.00))
                .andExpect(jsonPath("$.building").value("3号楼"));

        // 余额仅 2 元的 A3-102 取 10L（3 元）：扣费失败但记录保留
        mockMvc.perform(post("/api/residents/dispenses")
                        .header("X-Role", "RESIDENT").header("X-User-Id", "A3-102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceCode", "WQ-1001", "accountNo", "A3-102", "liters", 10.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeSucceeded").value(false))
                .andExpect(jsonPath("$.balanceAfter").value(2.00));
    }

    @Test
    void residentCanOnlyUseOwnAccount() throws Exception {
        mockMvc.perform(post("/api/residents/dispenses")
                        .header("X-Role", "RESIDENT").header("X-User-Id", "A3-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceCode", "WQ-1001", "accountNo", "A3-102", "liters", 1.0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsTelemetry_triggersLifeWarning() throws Exception {
        mockMvc.perform(post("/api/ops/telemetry")
                        .header("X-Role", "OPERATOR").header("X-User-Id", "ops-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"WQ-1002","filterLifePercent":10,"waterOutputLiters":2.0,
                                 "tds":40.0,"chlorine":0.5,"flowRate":2.0,"faultCode":"0"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ops/health/WQ-1002")
                        .header("X-Role", "OPERATOR").header("X-User-Id", "ops-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.dueSoon").value(true));
    }

    @Test
    void propertyDashboardAndCases() throws Exception {
        mockMvc.perform(get("/api/property/dashboard")
                        .header("X-Role", "PROPERTY").header("X-User-Id", "pm-1")
                        .header("X-Community-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceTotal").value(2));
        mockMvc.perform(get("/api/property/cases")
                        .header("X-Role", "PROPERTY").header("X-User-Id", "pm-1")
                        .header("X-Community-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void propertyWithoutCommunityHeader_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/property/dashboard")
                        .header("X-Role", "PROPERTY").header("X-User-Id", "pm-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void technicianEndpointRequiresNumericId() throws Exception {
        mockMvc.perform(get("/api/technician/tickets")
                        .header("X-Role", "TECHNICIAN").header("X-User-Id", "not-a-number"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUiAndApiDocs_available() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
