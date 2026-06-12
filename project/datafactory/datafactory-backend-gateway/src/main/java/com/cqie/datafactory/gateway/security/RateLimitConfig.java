package com.cqie.datafactory.gateway.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitConfig implements GlobalFilter, Ordered {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** 默认: 每个 IP 每分钟 100 次请求 */
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(100,
                Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 登录接口特殊限制: 每分钟 5 次
        if (path.startsWith("/auth/login")) {
            String ip = getClientIp(exchange);
            Bucket bucket = buckets.computeIfAbsent("login:" + ip, k -> {
                Bandwidth limit = Bandwidth.classic(5,
                        Refill.intervally(5, Duration.ofMinutes(1)));
                return Bucket.builder().addLimit(limit).build();
            });

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for login from IP: {}", ip);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        }

        // 通用限流
        String key = getClientIp(exchange);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket());

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP: {}", key);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
