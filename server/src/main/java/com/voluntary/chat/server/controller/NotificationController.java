package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.UpdateNotificationSettingsRequest;
import com.voluntary.chat.server.dto.response.NotificationSettingsResponse;
import com.voluntary.chat.server.service.NotificationSettingsService;
import com.voluntary.chat.server.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知设置控制器
 *
 * <p>
 * 提供通知设置的获取和更新接口。每次修改时客户端应调用 GET 刷新本地通知配置。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationSettingsService notificationSettingsService;

    /**
     * 获取当前用户的通知设置
     *
     * <p>
     * 返回用户的完整通知设置，包括消息通知、AI 问候、待办提醒、免打扰等。
     * 如果用户尚未设置，返回系统默认值。
     * </p>
     *
     * @return 通知设置
     */
    @GetMapping("/settings")
    public ApiResult<NotificationSettingsResponse> getSettings() {
        final Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取用户通知设置: userId={}", userId);
        final NotificationSettingsResponse settings = notificationSettingsService.getSettings(userId);
        return ApiResult.ok(settings);
    }

    /**
     * 更新当前用户的通知设置
     *
     * <p>
     * 支持部分更新，只传入需要修改的字段即可。
     * 请求中未设置的字段（null）保留原值不变。
     * </p>
     *
     * @param request 更新请求
     * @return 更新后的通知设置
     */
    @PutMapping("/settings")
    public ApiResult<NotificationSettingsResponse> updateSettings(
            @Valid @RequestBody final UpdateNotificationSettingsRequest request) {
        final Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户更新通知设置: userId={}", userId);
        final NotificationSettingsResponse settings = notificationSettingsService.updateSettings(userId, request);
        return ApiResult.ok(settings);
    }
}