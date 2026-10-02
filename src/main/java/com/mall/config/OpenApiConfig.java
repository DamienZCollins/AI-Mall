package com.mall.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mallOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Mall API")
                .description("模块化单体商城接口文档")
                .version("v0.0.1"));
    }
}
