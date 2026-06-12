package com.cqie.datafactory.configuration.config;

import com.cqie.datafactory.common.context.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String tenantIdHeader = req.getHeader("X-Tenant-Id");

        if (StringUtils.hasText(tenantIdHeader)) {
            try {
                TenantContext.set(Long.parseLong(tenantIdHeader));
                log.debug("Tenant context set to: {}", tenantIdHeader);
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Tenant-Id header: {}", tenantIdHeader);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
