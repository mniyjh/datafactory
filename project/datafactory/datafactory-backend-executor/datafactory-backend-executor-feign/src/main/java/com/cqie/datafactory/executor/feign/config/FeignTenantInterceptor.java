package com.cqie.datafactory.executor.feign.config;

import com.cqie.datafactory.common.context.TenantContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignTenantInterceptor {

    @Bean
    public RequestInterceptor tenantRequestInterceptor() {
        return (RequestTemplate template) -> {
            Long tenantId = TenantContext.get();
            if (tenantId != null) {
                template.header("X-Tenant-Id", tenantId.toString());
            }
        };
    }
}
