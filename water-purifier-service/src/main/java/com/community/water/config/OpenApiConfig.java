package com.community.water.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waterOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("城市社区净水机滤芯寿命报修换芯收费服务")
                .description("""
                        设备遥测上报 → 滤芯寿命动态评估 → 预警与工单调度 → 师傅换芯履约 →
                        收费与发票 → 复检与监管抽查，全部沉淀在同一设备履约链路中。
                        滤芯寿命并非固定日期：取水量、水质 TDS、维护频率与投诉都会改变换芯策略。
                        """)
                .version("1.0.0"));
    }
}
