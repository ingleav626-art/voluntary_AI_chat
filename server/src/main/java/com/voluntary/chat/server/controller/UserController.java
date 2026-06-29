package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.ChangePasswordRequest;
import com.voluntary.chat.server.dto.request.ChangePhoneRequest;
import com.voluntary.chat.server.dto.request.UpdateProfileRequest;
import com.voluntary.chat.server.dto.response.UserResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.AuthService;
import com.voluntary.chat.server.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/profile")
    public ApiResult<UserResponse> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        UserResponse response = userService.getProfile(userId);
        return ApiResult.ok(response);
    }

    @PutMapping("/profile")
    public ApiResult<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        userService.updateProfile(userId, request);
        return ApiResult.ok("修改成功", null);
    }

    @GetMapping("/search")
    public ApiResult<PageResult<UserResponse>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<UserResponse> result = userService.searchUsers(keyword, page, size);
        return ApiResult.ok(result);
    }

    /**
     * 发送验证码（用于密码修改）
     *
     * <p>根据当前登录用户自动获取手机号并发送验证码。</p>
     */
    @PostMapping("/password/sms")
    public ApiResult<Void> sendPasswordSms() {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.sendSmsCodeForUser(userId);
        return ApiResult.ok("验证码已发送", null);
    }

    /**
     * 修改密码（验证当前手机号短信码）
     */
    @PutMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.changePassword(userId, request.getSmsCode(), request.getNewPassword(), request.getConfirmPassword());
        return ApiResult.ok("密码修改成功", null);
    }

    /**
     * 修改手机号（验证旧手机和新手机短信码）
     */
    @PutMapping("/phone")
    public ApiResult<Void> changePhone(@Valid @RequestBody ChangePhoneRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.changePhone(userId, request.getSmsCode(), request.getNewPhone(), request.getNewSmsCode());
        return ApiResult.ok("手机号修改成功", null);
    }
}
