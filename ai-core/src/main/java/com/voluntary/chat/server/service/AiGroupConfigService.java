package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.AiGroupConfigRequest;
import com.voluntary.chat.server.dto.response.AiGroupConfigResponse;
import com.voluntary.chat.server.entity.AiGroupConfig;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.mapper.AiGroupConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 群配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGroupConfigService {

    private final AiGroupConfigMapper aiGroupConfigMapper;
    private final AiService aiService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private static final String COOLDOWN_KEY_PREFIX = "ai:cooldown:";
    private static final int DEFAULT_COOLDOWN_SECONDS = 30;
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final int EXTRA_EXPIRE_SECONDS = 10;

    @Transactional
    public Long createGroupConfig(final Long groupId, final Long userId, final AiGroupConfigRequest request) {
        final AiProfile profile = aiService.getAiProfileById(request.getAiId());

        final LambdaQueryWrapper<AiGroupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGroupConfig::getGroupId, groupId)
                .eq(AiGroupConfig::getAiId, request.getAiId());
        final AiGroupConfig existing = aiGroupConfigMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该 AI 已配置在此群中");
        }

        final AiGroupConfig config = new AiGroupConfig();
        config.setGroupId(groupId);
        config.setAiId(request.getAiId());
        config.setTriggerKeywords(request.getTriggerKeywords());
        config.setTriggerProbability(request.getTriggerProbability() != null ? request.getTriggerProbability() : 0.0);
        config.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
        config.setCooldownSeconds(
                request.getCooldownSeconds() != null ? request.getCooldownSeconds() : DEFAULT_COOLDOWN_SECONDS);
        config.setIsDeleted(0);

        aiGroupConfigMapper.insert(config);

        log.info("群 AI 配置创建成功: groupId={}, aiId={}, userId={}", groupId, request.getAiId(), userId);
        return config.getId();
    }

    public List<AiGroupConfigResponse> getGroupConfigs(final Long groupId) {
        final LambdaQueryWrapper<AiGroupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGroupConfig::getGroupId, groupId)
                .eq(AiGroupConfig::getIsEnabled, true);

        final List<AiGroupConfig> configs = aiGroupConfigMapper.selectList(wrapper);

        return configs.stream()
                .map(config -> {
                    final AiProfile profile = aiService.getAiProfileById(config.getAiId());
                    return AiGroupConfigResponse.builder()
                            .configId(config.getId())
                            .aiId(config.getAiId())
                            .aiName(profile.getName())
                            .triggerKeywords(config.getTriggerKeywords())
                            .triggerProbability(config.getTriggerProbability())
                            .isEnabled(config.getIsEnabled())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public boolean checkTrigger(final Long groupId, final String content, final Long aiId) {
        final LambdaQueryWrapper<AiGroupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGroupConfig::getGroupId, groupId)
                .eq(AiGroupConfig::getAiId, aiId)
                .eq(AiGroupConfig::getIsEnabled, true);

        final AiGroupConfig config = aiGroupConfigMapper.selectOne(wrapper);
        if (config == null) {
            return false;
        }

        if (isInCooldown(groupId, aiId, config.getCooldownSeconds())) {
            log.debug("AI 冷却中: groupId={}, aiId={}", groupId, aiId);
            return false;
        }

        final AiProfile profile = aiService.getAiProfileById(aiId);
        if (content.contains("@") && content.contains(profile.getName())) {
            setCooldown(groupId, aiId, config.getCooldownSeconds());
            return true;
        }

        if (config.getTriggerKeywords() != null && !config.getTriggerKeywords().isEmpty()) {
            final String[] keywords = config.getTriggerKeywords().split(",");
            for (final String keyword : keywords) {
                if (content.contains(keyword.trim())) {
                    setCooldown(groupId, aiId, config.getCooldownSeconds());
                    return true;
                }
            }
        }

        if (config.getTriggerProbability() != null && config.getTriggerProbability() > 0) {
            final double random = Math.random();
            if (random < config.getTriggerProbability()) {
                setCooldown(groupId, aiId, config.getCooldownSeconds());
                return true;
            }
        }

        return false;
    }

    public List<AiGroupConfig> getEnabledConfigs(final Long groupId) {
        final LambdaQueryWrapper<AiGroupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGroupConfig::getGroupId, groupId)
                .eq(AiGroupConfig::getIsEnabled, true);
        return aiGroupConfigMapper.selectList(wrapper);
    }

    private boolean isInCooldown(final Long groupId, final Long aiId, final int cooldownSeconds) {
        if (redisTemplate == null) {
            return false;
        }
        final String key = COOLDOWN_KEY_PREFIX + groupId + ":" + aiId;
        final String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }

        final long lastReplyTime = Long.parseLong(value);
        final long elapsed = System.currentTimeMillis() - lastReplyTime;
        return elapsed < cooldownSeconds * MILLIS_PER_SECOND;
    }

    private void setCooldown(final Long groupId, final Long aiId, final int cooldownSeconds) {
        if (redisTemplate == null) {
            return;
        }
        final String key = COOLDOWN_KEY_PREFIX + groupId + ":" + aiId;
        redisTemplate.opsForValue().set(
                key,
                String.valueOf(System.currentTimeMillis()),
                cooldownSeconds + EXTRA_EXPIRE_SECONDS,
                TimeUnit.SECONDS);
    }
}
