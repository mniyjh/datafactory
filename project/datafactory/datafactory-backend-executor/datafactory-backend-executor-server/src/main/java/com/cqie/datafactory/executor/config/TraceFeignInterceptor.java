package com.cqie.datafactory.executor.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Feign 调用时自动携带当前 traceId */
@Configuration
public class TraceFeignInterceptor {
    @Bean
    public RequestInterceptor traceRequestInterceptor() {
        return (RequestTemplate template) -> {
            String traceId = MDC.get("traceId");
            if (traceId != null) {
                template.header("X-Trace-Id", traceId);
            }
        };
    }
}
