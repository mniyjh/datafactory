package com.cqie.datafactory.configuration.service.impl;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.configuration.controller.dto.LoginDTO;
import com.cqie.datafactory.configuration.controller.vo.LoginVO;
import com.cqie.datafactory.configuration.entity.Tenant;
import com.cqie.datafactory.configuration.entity.TokenBlacklist;
import com.cqie.datafactory.configuration.entity.User;
import com.cqie.datafactory.configuration.mapper.TenantMapper;
import com.cqie.datafactory.configuration.mapper.TokenBlacklistMapper;
import com.cqie.datafactory.configuration.mapper.UserMapper;
import com.cqie.datafactory.configuration.security.JwtService;
import com.cqie.datafactory.configuration.service.AuthService;
import com.cqie.datafactory.configuration.service.EmailService;
import com.cqie.datafactory.configuration.service.VerificationCodeManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TenantMapper tenantMapper;
    private final TokenBlacklistMapper tokenBlacklistMapper;
    private final EmailService emailService;
    private final VerificationCodeManager codeManager;

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionsByUserId(user.getId());

        List<Tenant> tenants = tenantMapper.selectByUserId(user.getId());
        List<LoginVO.TenantInfo> tenantInfos = tenants.stream().map(t ->
                LoginVO.TenantInfo.builder().id(t.getId()).name(t.getName()).code(t.getCode()).build()
        ).collect(Collectors.toList());

        List<Long> tenantIds = tenants.stream().map(Tenant::getId).collect(Collectors.toList());

        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getUsername(), roles, permissions, tenantIds);
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        log.info("用户 {} 登录成功, 角色: {}, 权限数: {}, 租户数: {}", user.getUsername(), roles, permissions.size(), tenantInfos.size());

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400)
                .tokenType("Bearer")
                .user(LoginVO.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .email(user.getEmail())
                        .roles(roles)
                        .permissions(permissions)
                        .tenants(tenantInfos)
                        .build())
                .build();
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new BusinessException("Refresh Token 无效或已过期");
        }

        // Validate this is a refresh token, not an access token
        String tokenType = jwtService.parseToken(refreshToken).get("type", String.class);
        if (!"refresh".equals(tokenType)) {
            throw new BusinessException("无效的Token类型");
        }

        Long userId = jwtService.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);

        if (user == null || user.getStatus() == 0) {
            throw new BusinessException("用户不存在或已被禁用");
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        List<String> permissions = userMapper.selectPermissionsByUserId(userId);

        List<Tenant> tenants = tenantMapper.selectByUserId(userId);
        List<Long> tenantIds = tenants.stream().map(Tenant::getId).collect(Collectors.toList());
        List<LoginVO.TenantInfo> tenantInfos = tenants.stream().map(t ->
                LoginVO.TenantInfo.builder().id(t.getId()).name(t.getName()).code(t.getCode()).build()
        ).collect(Collectors.toList());

        String newAccessToken = jwtService.generateAccessToken(
                userId, user.getUsername(), roles, permissions, tenantIds);

        return LoginVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400)
                .tokenType("Bearer")
                .user(LoginVO.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .email(user.getEmail())
                        .roles(roles)
                        .permissions(permissions)
                        .tenants(tenantInfos)
                        .build())
                .build();
    }

    @Override
    public void logout(Long userId, String accessToken) {
        if (userId == null || accessToken == null) return;
        try {
            Claims claims = jwtService.parseToken(accessToken);
            String jti = claims.getId();
            if (jti != null) {
                TokenBlacklist bl = new TokenBlacklist();
                bl.setJti(jti);
                bl.setUserId(userId);
                bl.setExpiresAt(claims.getExpiration().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                tokenBlacklistMapper.insert(bl);
                log.info("Token blacklisted: userId={}, jti={}", userId, jti);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    @Override
    public void sendVerificationCode(String username, String email) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BusinessException("用户名不存在");
        }
        if (!email.equals(user.getEmail())) {
            throw new BusinessException("用户名与邮箱不匹配");
        }
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        if (roles.contains("super_admin")) {
            throw new BusinessException("超级管理员不能使用忘记密码功能");
        }
        String code = codeManager.generate(username + ":" + email);
        emailService.sendVerificationCode(email, code);
        log.info("Verification code sent to user {} at {}", username, email);
    }

    @Override
    public void resetPassword(String username, String email, String code) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BusinessException("用户名不存在");
        }
        if (!codeManager.verify(username + ":" + email, code)) {
            throw new BusinessException("验证码错误或已过期");
        }
        String encodedPwd = passwordEncoder.encode("123456");
        user.setPassword(encodedPwd);
        userMapper.updateById(user);
        log.info("Password reset for user {}", username);
    }
}
