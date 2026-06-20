package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.LoginRequest;
import com.voluntary.chat.server.dto.request.RefreshTokenRequest;
import com.voluntary.chat.server.dto.request.RegisterRequest;
import com.voluntary.chat.server.dto.request.SmsSendRequest;
import com.voluntary.chat.server.dto.response.LoginResponse;
import com.voluntary.chat.server.dto.response.RefreshTokenResponse;
import com.voluntary.chat.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sms/send")
    public ApiResult<Void> sendSmsCode(@Valid @RequestBody SmsSendRequest request) {
        authService.sendSmsCode(request.getPhone());
        return ApiResult.ok("验证码已发送", null);
    }

    @PostMapping("/register")
    public ApiResult<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ApiResult.ok(response);
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResult.ok(response);
    }

    @PostMapping("/refresh")
    public ApiResult<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = authService.refresh(request);
        return ApiResult.ok(response);
    }
}
