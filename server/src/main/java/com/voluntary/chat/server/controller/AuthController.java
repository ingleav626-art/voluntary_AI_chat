package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.ForgotPasswordRequest;
import com.voluntary.chat.server.dto.request.LoginRequest;
import com.voluntary.chat.server.dto.request.RefreshTokenRequest;
import com.voluntary.chat.server.dto.request.RegisterRequest;
import com.voluntary.chat.server.dto.request.SmsSendRequest;
import com.voluntary.chat.server.dto.response.LoginResponse;
import com.voluntary.chat.server.dto.response.RefreshTokenResponse;
import com.voluntary.chat.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    @PostMapping("/sms/send")
    public ApiResult<Void> sendSmsCode(@Valid @RequestBody SmsSendRequest request) {
        LOG.info("收到发送验证码请求: phone={}", request.getPhone());
        authService.sendSmsCode(request.getPhone());
        return ApiResult.ok("验证码已发送", null);
    }

    @PostMapping("/register")
    public ApiResult<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LOG.info("收到注册请求: phone={}, username={}", request.getPhone(), request.getUsername());
        LoginResponse response = authService.register(request);
        return ApiResult.ok(response);
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LOG.info("收到登录请求: phone={}", request.getPhone());
        LoginResponse response = authService.login(request);
        return ApiResult.ok(response);
    }

    @PostMapping("/refresh")
    public ApiResult<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LOG.info("收到刷新Token请求");
        RefreshTokenResponse response = authService.refresh(request);
        return ApiResult.ok(response);
    }

    @PostMapping("/forgot-password")
    public ApiResult<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        LOG.info("收到忘记密码请求: phone={}", request.getPhone());
        authService.forgotPassword(request.getPhone(), request.getCode(),
                request.getNewPassword(), request.getConfirmPassword());
        return ApiResult.ok("密码重置成功", null);
    }
}
