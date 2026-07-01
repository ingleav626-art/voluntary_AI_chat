package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.dto.request.CreateAiProfileRequest;
import com.voluntary.chat.server.dto.request.UpdateAiProfileRequest;
import com.voluntary.chat.server.dto.response.AiProfileResponse;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.mapper.AiProfileMapper;
import com.voluntary.chat.server.util.AesKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 角色管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProfileMapper aiProfileMapper;
    private final AiConfig aiConfig;

    /**
     * 获取加密密钥，未配置时抛出业务异常
     *
     * @return 加密密钥
     */
    private String getEncryptionKey() {
        final String key = aiConfig.getEncryptionKey();
        if (key == null || key.isEmpty()) {
            log.error("AI 加密密钥未配置，请设置 ai.encryption-key 或环境变量 AI_ENCRYPTION_KEY");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 加密密钥未配置");
        }
        return key;
    }

    /**
     * 获取用户的 AI 列表
     */
    public PageResult<AiProfileResponse> listAiProfiles(final Long userId, final Integer page, final Integer size) {
        final LambdaQueryWrapper<AiProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiProfile::getUserId, userId)
                .eq(AiProfile::getStatus, 0)
                .orderByDesc(AiProfile::getCreateTime);

        final Page<AiProfile> pageObj = new Page<>(page, size);
        final Page<AiProfile> result = aiProfileMapper.selectPage(pageObj, wrapper);

        final List<AiProfileResponse> list = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, result.getTotal(), page, size);
    }

    /**
     * 创建 AI 角色
     */
    @Transactional
    public Long createAiProfile(final Long userId, final CreateAiProfileRequest request) {
        // 加密 API Key
        final String apiKeyEnc = AesKeyUtil.encrypt(request.getApiKey(), getEncryptionKey());

        final AiProfile profile = new AiProfile();
        profile.setUserId(userId);
        profile.setName(request.getName());
        profile.setAvatar(request.getAvatar());
        profile.setPersona(request.getPersona());
        profile.setSystemPrompt(request.getSystemPrompt());
        profile.setModelProvider(request.getModelProvider());
        profile.setModel(request.getModel());
        profile.setApiKeyEnc(apiKeyEnc);
        profile.setBaseUrl(request.getBaseUrl());
        profile.setIsGroup(request.getIsGroup() != null ? request.getIsGroup() : false);
        profile.setTemperature(
                request.getTemperature() != null ? request.getTemperature() : aiConfig.getDefaultTemperature());
        profile.setMaxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : aiConfig.getDefaultMaxTokens());
        profile.setStatus(0);
        profile.setIsDeleted(0);

        aiProfileMapper.insert(profile);

        log.info("AI 角色创建成功: aiId={}, userId={}, name={}", profile.getId(), userId, request.getName());
        return profile.getId();
    }

    /**
     * 修改 AI 角色
     */
    @Transactional
    public void updateAiProfile(final Long userId, final Long aiId, final UpdateAiProfileRequest request) {
        final AiProfile profile = getAiProfileById(aiId);

        // 检查所有权
        if (!profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 更新字段
        if (request.getName() != null) {
            profile.setName(request.getName());
        }
        if (request.getAvatar() != null) {
            profile.setAvatar(request.getAvatar());
        }
        if (request.getPersona() != null) {
            profile.setPersona(request.getPersona());
        }
        if (request.getSystemPrompt() != null) {
            profile.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getModel() != null) {
            profile.setModel(request.getModel());
        }
        if (request.getApiKey() != null) {
            final String apiKeyEnc = AesKeyUtil.encrypt(request.getApiKey(), getEncryptionKey());
            profile.setApiKeyEnc(apiKeyEnc);
        }
        if (request.getBaseUrl() != null) {
            profile.setBaseUrl(request.getBaseUrl());
        }
        if (request.getIsGroup() != null) {
            profile.setIsGroup(request.getIsGroup());
        }
        if (request.getTemperature() != null) {
            profile.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            profile.setMaxTokens(request.getMaxTokens());
        }

        aiProfileMapper.updateById(profile);

        log.info("AI 角色修改成功: aiId={}, userId={}", aiId, userId);
    }

    /**
     * 删除 AI 角色
     */
    @Transactional
    public void deleteAiProfile(final Long userId, final Long aiId) {
        final AiProfile profile = getAiProfileById(aiId);

        // 检查所有权
        if (!profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        aiProfileMapper.deleteById(aiId);

        log.info("AI 角色删除成功: aiId={}, userId={}", aiId, userId);
    }

    /**
     * 获取 AI 角色详情
     */
    public AiProfile getAiProfileById(final Long aiId) {
        final AiProfile profile = aiProfileMapper.selectById(aiId);
        if (profile == null || profile.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.AI_NOT_FOUND);
        }
        if (profile.getStatus() != 0) {
            throw new BusinessException(ErrorCode.AI_DISABLED);
        }
        return profile;
    }

    /**
     * 解密 API Key
     */
    public String decryptApiKey(final AiProfile profile) {
        return AesKeyUtil.decrypt(profile.getApiKeyEnc(), getEncryptionKey());
    }

    /**
     * 转换为响应对象
     */
    private AiProfileResponse toResponse(final AiProfile profile) {
        return AiProfileResponse.builder()
                .aiId(profile.getId())
                .name(profile.getName())
                .avatar(profile.getAvatar())
                .persona(profile.getPersona())
                .systemPrompt(profile.getSystemPrompt())
                .modelProvider(profile.getModelProvider())
                .model(profile.getModel())
                .baseUrl(profile.getBaseUrl())
                .isGroup(profile.getIsGroup())
                .temperature(profile.getTemperature())
                .maxTokens(profile.getMaxTokens())
                .build();
    }
}