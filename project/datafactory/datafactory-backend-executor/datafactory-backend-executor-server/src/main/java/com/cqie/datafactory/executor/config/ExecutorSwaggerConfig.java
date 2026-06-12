package com.cqie.datafactory.executor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorSwaggerConfig {
    @Bean
    public OpenAPI executorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataFactory Executor API")
                        .description("执行引擎服务 — 任务执行/调度/日志查询")
                        .version("1.0.0")
                        .contact(new Contact().name("DataFactory Team")));
    }
}
