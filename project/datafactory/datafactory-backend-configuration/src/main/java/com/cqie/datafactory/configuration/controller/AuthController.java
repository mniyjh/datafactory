package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.ForgotPasswordDTO;
import com.cqie.datafactory.configuration.controller.dto.LoginDTO;
import com.cqie.datafactory.configuration.controller.vo.LoginVO;
import com.cqie.datafactory.configuration.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return Result.success(authService.refresh(refreshToken));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute(value = "userId", required = false) Long userId,
                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        authService.logout(userId, accessToken);
        return Result.success();
    }

    @Operation(summary = "发送密码重置验证码")
    @PostMapping("/forgot-password/send-code")
    public Result<Void> sendVerificationCode(@Valid @RequestBody ForgotPasswordDTO.SendCodeRequest req) {
        authService.sendVerificationCode(req.getUsername(), req.getEmail());
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/forgot-password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ForgotPasswordDTO.ResetRequest req) {
        authService.resetPassword(req.getUsername(), req.getEmail(), req.getCode());
        return Result.success();
    }
}
