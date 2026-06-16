package com.cqie.datafactory.configuration.security;

import com.cqie.datafactory.configuration.mapper.TokenBlacklistMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private final TokenBlacklistMapper tokenBlacklistMapper;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        // Ensure key is at least 256 bits for HS256
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 生成 Access Token */
    public String generateAccessToken(Long userId, String username,
                                       List<String> roles, List<String> permissions,
                                       List<Long> tenantIds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("tenantIds", tenantIds != null ? tenantIds : List.of())
                .issuedAt(now)
                .expiration(expiry)
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** 生成 Refresh Token */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** 解析 JWT Claims (不抛异常) */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 检查Token是否被拉黑 */
    public boolean isBlacklisted(String token) {
        try {
            Claims claims = parseToken(token);
            String jti = claims.getId();
            return jti != null && tokenBlacklistMapper.existsByJti(jti);
        } catch (Exception e) {
            return true; // 解析失败视为无效
        }
    }

    /** 验证 Token 是否有效 */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return !isBlacklisted(token);
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /** 从 Token 中提取 userId */
    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /** 从 Token 中提取用户名 */
    public String getUsernameFromToken(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 从 Token 中提取角色列表 */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return parseToken(token).get("roles", List.class);
    }

    /** 从 Token 中提取权限列表 */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        return parseToken(token).get("permissions", List.class);
    }

    /** 从 Token 中提取租户ID列表 */
    @SuppressWarnings("unchecked")
    public List<Long> getTenantIdsFromToken(String token) {
        List<?> raw = parseToken(token).get("tenantIds", List.class);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(o -> o instanceof Number ? ((Number) o).longValue() : Long.parseLong(o.toString()))
                .toList();
    }
}
