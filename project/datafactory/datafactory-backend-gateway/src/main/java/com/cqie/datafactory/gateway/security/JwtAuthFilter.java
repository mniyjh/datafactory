package com.cqie.datafactory.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${security.internal-auth-key:}")
    private String internalAuthKey;

    private static final List<String> WHITELIST = List.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/forgot-password",
            "/actuator/health",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 先读取原始请求中的 X-Tenant-Id（用于后续校验），然后清除所有安全敏感头
        String requestedTenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");

        // Strip any incoming X-User-* headers to prevent spoofing, add internal auth key
        ServerHttpRequest cleanedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Username");
                    h.remove("X-User-Roles");
                    h.remove("X-User-Permissions");
                    h.remove("X-Tenant-Id");
                    h.remove("X-Internal-Auth");
                    if (StringUtils.hasText(internalAuthKey)) {
                        h.set("X-Internal-Auth", internalAuthKey);
                    }
                })
                .build();
        exchange = exchange.mutate().request(cleanedRequest).build();

        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // OPTIONS 预检放行
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parseToken(token);

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            @SuppressWarnings("unchecked")
            List<?> tenantIdsRaw = claims.get("tenantIds", List.class);
            List<Long> tenantIds = tenantIdsRaw != null
                    ? tenantIdsRaw.stream()
                        .map(o -> o instanceof Number ? ((Number) o).longValue() : Long.parseLong(o.toString()))
                        .toList()
                    : List.of();

            // 解析并校验租户ID：前端传入的 X-Tenant-Id 必须在 JWT 授权的租户列表中
            String resolvedTenantId = resolveTenantId(requestedTenantId, tenantIds);

            // 透传用户信息到下游微服务
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Username", username)
                    .header("X-User-Roles", roles != null ? String.join(",", roles) : "")
                    .header("X-User-Permissions", permissions != null ? String.join(",", permissions) : "")
                    .header("X-Tenant-Id", resolvedTenantId)
                    .build();

            log.debug("JWT validated: user={} userId={} tenant={} path={}", username, userId, resolvedTenantId, path);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.debug("JWT validation failed for path {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * 解析租户ID：如果前端传入的租户ID在JWT授权的租户列表中则使用，否则使用第一个
     */
    private String resolveTenantId(String requestedTenantId, List<Long> tenantIds) {
        if (tenantIds.isEmpty()) {
            return "0"; // 无租户权限，返回0（不会匹配任何数据）
        }
        if (StringUtils.hasText(requestedTenantId)) {
            try {
                Long requested = Long.parseLong(requestedTenantId);
                if (tenantIds.contains(requested)) {
                    return requestedTenantId;
                }
                log.warn("Tenant {} not in user's allowed tenants {}, falling back to first", requested, tenantIds);
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Tenant-Id format: {}", requestedTenantId);
            }
        }
        return String.valueOf(tenantIds.get(0));
    }

    private Claims parseToken(String token) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
