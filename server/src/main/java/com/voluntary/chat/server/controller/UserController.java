package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.UpdateProfileRequest;
import com.voluntary.chat.server.dto.response.UserResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
