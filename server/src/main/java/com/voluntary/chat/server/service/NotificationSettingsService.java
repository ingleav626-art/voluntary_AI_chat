package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.server.dto.request.UpdateNotificationSettingsRequest;
import com.voluntary.chat.server.dto.response.NotificationSettingsResponse;
import com.voluntary.chat.server.entity.NotificationSettings;
import com.voluntary.chat.server.mapper.NotificationSettingsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 通知设置服务
 *
 * <p>
 * 每个用户有且仅有一条通知设置记录，支持获取和更新。不存在时返回默认设置。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final NotificationSettingsMapper mapper;

    /**
     * 获取用户的通知设置
     *
     * @param userId 用户ID
     * @return 通知设置，不存在时返回默认设置
     */
    public NotificationSettingsResponse getSettings(final Long userId) {
        final NotificationSettings entity = findByUserId(userId);
        if (entity == null) {
            return NotificationSettingsResponse.defaultSettings();
        }
        return NotificationSettingsResponse.fromEntity(entity);
    }

    /**
     * 更新用户的通知设置
     *
     * <p>
     * 只更新请求中非 null 的字段，保留其他字段原值。
     * 设置记录不存在时自动创建。
     * </p>
     *
     * @param userId  用户ID
     * @param request 更新请求
     * @return 更新后的通知设置
     */
    @Transactional
    public NotificationSettingsResponse updateSettings(final Long userId,
                                                       final UpdateNotificationSettingsRequest request) {
        NotificationSettings entity = findByUserId(userId);

        if (entity == null) {
            // 不存在则创建新的设置记录
            entity = createDefault(userId);
        }

        // 只更新请求中非 null 的字段
        mergeFields(entity, request);
        entity.setUpdateTime(LocalDateTime.now());
        mapper.updateById(entity);

        log.info("用户通知设置已更新: userId={}", userId);
        return NotificationSettingsResponse.fromEntity(entity);
    }

    /**
     * 判断用户当前是否在免打扰时段
     *
     * @param userId 用户ID
     * @return true=在免打扰时段内
     */
    public boolean isInDoNotDisturb(final Long userId) {
        final NotificationSettings entity = findByUserId(userId);
        if (entity == null || !Boolean.TRUE.equals(entity.getDoNotDisturb())) {
            return false;
        }

        final LocalTime now = LocalTime.now();
        final LocalTime start = entity.getDndStartTime();
        final LocalTime end = entity.getDndEndTime();

        if (start == null || end == null) {
            return false;
        }

        // 跨天情况：22:00-09:00
        if (start.isAfter(end)) {
            return now.isAfter(start) || now.isBefore(end);
        }
        // 同天情况：13:00-14:00
        return now.isAfter(start) && now.isBefore(end);
    }

    /**
     * 按用户ID查找设置
     */
    private NotificationSettings findByUserId(final Long userId) {
        final LambdaQueryWrapper<NotificationSettings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationSettings::getUserId, userId);
        return mapper.selectOne(wrapper);
    }

    /**
     * 创建默认设置记录
     */
    private NotificationSettings createDefault(final Long userId) {
        final NotificationSettings entity = new NotificationSettings();
        entity.setUserId(userId);
        entity.setMessageNotification(true);
        entity.setMessageSound(true);
        entity.setAiGreetingNotification(true);
        entity.setAiGreetingSound(true);
        entity.setTodoReminder(true);
        entity.setTodoSound(true);
        entity.setDoNotDisturb(false);
        entity.setDndStartTime(LocalTime.of(22, 0));
        entity.setDndEndTime(LocalTime.of(9, 0));
        entity.setMergeWindowSeconds(5);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        mapper.insert(entity);
        log.info("用户默认通知设置已创建: userId={}", userId);
        return entity;
    }

    /**
     * 合并更新字段（只覆盖非 null 值）
     */
    private void mergeFields(final NotificationSettings entity,
                             final UpdateNotificationSettingsRequest request) {
        if (request.getMessageNotification() != null) {
            entity.setMessageNotification(request.getMessageNotification());
        }
        if (request.getMessageSound() != null) {
            entity.setMessageSound(request.getMessageSound());
        }
        if (request.getAiGreetingNotification() != null) {
            entity.setAiGreetingNotification(request.getAiGreetingNotification());
        }
        if (request.getAiGreetingSound() != null) {
            entity.setAiGreetingSound(request.getAiGreetingSound());
        }
        if (request.getTodoReminder() != null) {
            entity.setTodoReminder(request.getTodoReminder());
        }
        if (request.getTodoSound() != null) {
            entity.setTodoSound(request.getTodoSound());
        }
        if (request.getDoNotDisturb() != null) {
            entity.setDoNotDisturb(request.getDoNotDisturb());
        }
        if (request.getDndStartTime() != null) {
            entity.setDndStartTime(request.getDndStartTime());
        }
        if (request.getDndEndTime() != null) {
            entity.setDndEndTime(request.getDndEndTime());
        }
        if (request.getMergeWindowSeconds() != null) {
            entity.setMergeWindowSeconds(request.getMergeWindowSeconds());
        }
    }
}
