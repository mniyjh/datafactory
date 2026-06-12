package com.cqie.datafactory.configuration.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /** JWT签名密钥 (HMAC-SHA256, 最少256位) */
    private String secret = "${JWT_SECRET:}";
    /** Access Token 过期时间(秒), 默认24小时 */
    private long accessTokenExpiration = 86400;
    /** Refresh Token 过期时间(秒), 默认7天 */
    private long refreshTokenExpiration = 604800;
    /** Token 签发者 */
    private String issuer = "datafactory";
}
