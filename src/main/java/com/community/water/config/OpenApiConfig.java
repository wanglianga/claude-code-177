package com.community.water.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waterOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("社区净水机滤芯履约服务 API")
                        .version("1.0.0")
                        .description("""
                                城市社区净水机滤芯寿命报修换芯收费服务。
                                统一设备履约链路：设备-滤芯-取水-投诉-报修-派单-换芯-复检-收费-发票。
                                鉴权方式：所有 /api/** 请求携带请求头 X-Role（RESIDENT/TECHNICIAN/PROPERTY/OPERATOR）、
                                X-User-Id，物业与居民请求附带 X-Community-Id 做小区隔离。""")
                        .license(new License().name("内部演示").url("https://example.com")))
                .components(new Components().addSecuritySchemes("roleHeaders",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-Role")))
                .addSecurityItem(new SecurityRequirement().addList("roleHeaders"));
    }
}
