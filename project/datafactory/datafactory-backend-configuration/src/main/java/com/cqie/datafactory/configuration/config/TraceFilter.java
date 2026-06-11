package com.cqie.datafactory.configuration.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/** 为每个 HTTP 请求生成 traceId，注入 MDC 和响应头，实现全链路追踪 */
@Component
@Order(0)
public class TraceFilter implements Filter {
    private static final String TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String traceId = null;
        if (req instanceof HttpServletRequest httpReq) {
            traceId = httpReq.getHeader("X-Trace-Id");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        MDC.put(TRACE_ID, traceId);
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
