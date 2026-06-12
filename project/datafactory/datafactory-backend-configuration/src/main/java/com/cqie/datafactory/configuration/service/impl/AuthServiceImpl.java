package com.cqie.datafactory.configuration.service.impl;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.configuration.controller.dto.LoginDTO;
import com.cqie.datafactory.configuration.controller.vo.LoginVO;
import com.cqie.datafactory.configuration.entity.User;
import com.cqie.datafactory.configuration.mapper.UserMapper;
import com.cqie.datafactory.configuration.security.JwtService;
import com.cqie.datafactory.configuration.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

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

        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getUsername(), roles, permissions);
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        log.info("用户 {} 登录成功, 角色: {}, 权限数: {}", user.getUsername(), roles, permissions.size());

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

        String newAccessToken = jwtService.generateAccessToken(
                userId, user.getUsername(), roles, permissions);

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
                        .build())
                .build();
    }

    @Override
    public void logout(Long userId) {
        log.info("用户 {} 已登出", userId);
        // Token黑名单可在此扩展 (Redis)
    }
}
