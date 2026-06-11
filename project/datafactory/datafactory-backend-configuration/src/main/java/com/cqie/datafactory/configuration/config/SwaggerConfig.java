package com.cqie.datafactory.configuration.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI configurationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataFactory Configuration API")
                        .description("配置管理服务 — 脚本/数据源/API/组件/调度")
                        .version("1.0.0")
                        .contact(new Contact().name("DataFactory Team")));
    }
}
