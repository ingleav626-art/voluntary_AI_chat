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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 群配置服务
 */
@Slf4j
@RequiredArgsConstructor
public class AiGroupConfigService {

    private final AiGroupConfigMapper aiGroupConfigMapper;
    private final AiService aiService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** 云端模式是否启用（本地模式下AI存储在客户端，服务端不验证AI存在） */
    @Value("${cloud.mode.enabled:false}")
    private boolean cloudModeEnabled;

    private static final String COOLDOWN_KEY_PREFIX = "ai:cooldown:";
    /** 默认冷却时间（秒） */
    private static final int DEFAULT_COOLDOWN_SECONDS = 30;
    /** 毫秒转换因子 */
    private static final long MILLIS_PER_SECOND = 1000L;
    /** Redis键额外过期时间（秒） */
    private static final int EXTRA_EXPIRE_SECONDS = 10;

    /**
     * 创建群 AI 配置
     */
    @Transactional
    public Long createGroupConfig(final Long groupId, final Long userId, final AiGroupConfigRequest request) {
        // 云端模式下检查 AI 是否存在且可用（本地模式下AI存储在客户端，跳过验证）
        if (cloudModeEnabled) {
            final AiProfile profile = aiService.getAiProfileById(request.getAiId());
            log.debug("云端模式验证AI存在: aiId={}, name={}", request.getAiId(), profile.getName());
        } else {
            log.debug("本地模式跳过AI验证: aiId={}", request.getAiId());
        }

        // 检查是否已存在配置（包括已删除的，使用原生SQL绕过逻辑删除）
        final AiGroupConfig existing = aiGroupConfigMapper.selectByGroupAndAiIgnoreDeleted(groupId, request.getAiId());

        if (existing != null) {
            // 如果已存在但已删除，恢复它
            if (existing.getIsDeleted() != null && existing.getIsDeleted() == 1) {
                existing.setIsDeleted(0);
                existing.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
                existing.setTriggerKeywords(request.getTriggerKeywords());
                existing.setTriggerProbability(request.getTriggerProbability() != null ? request.getTriggerProbability() : 0.0);
                existing.setCooldownSeconds(request.getCooldownSeconds() != null ? request.getCooldownSeconds() : DEFAULT_COOLDOWN_SECONDS);
                aiGroupConfigMapper.updateById(existing);
                log.info("群 AI 配置恢复成功: groupId={}, aiId={}, userId={}, configId={}",
                        groupId, request.getAiId(), userId, existing.getId());
                return existing.getId();
            }
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

    /**
     * 获取群 AI 配置列表
     */
    public List<AiGroupConfigResponse> getGroupConfigs(final Long groupId) {
        final LambdaQueryWrapper<AiGroupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGroupConfig::getGroupId, groupId)
                .eq(AiGroupConfig::getIsEnabled, true);

        final List<AiGroupConfig> configs = aiGroupConfigMapper.selectList(wrapper);

        return configs.stream()
                .map(config -> {
                    // 云端模式从服务端数据库获取AI名称，本地模式返回占位符（客户端自行补充）
                    String aiName;
                    if (cloudModeEnabled) {
                        final AiProfile profile = aiService.getAiProfileById(config.getAiId());
                        aiName = profile.getName();
                    } else {
                        aiName = "AI #" + config.getAiId(); // 占位符，客户端本地引擎补充真实名称
                    }
                    return AiGroupConfigResponse.builder()
                            .configId(config.getId())
                            .aiId(config.getAiId())
                            .aiName(aiName)
                            .triggerKeywords(config.getTriggerKeywords())
                            .triggerProbability(config.getTriggerProbability())
                            .isEnabled(config.getIsEnabled())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 修改群 AI 配置
     */
    @Transactional
    public void updateGroupConfig(final Long groupId, final Long configId, final Long userId,
            final AiGroupConfigRequest request) {
        final AiGroupConfig config = aiGroupConfigMapper.selectById(configId);
        if (config == null || !config.getGroupId().equals(groupId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配置不存在");
        }

        if (request.getTriggerKeywords() != null) {
            config.setTriggerKeywords(request.getTriggerKeywords());
        }
        if (request.getTriggerProbability() != null) {
            config.setTriggerProbability(request.getTriggerProbability());
        }
        if (request.getIsEnabled() != null) {
            config.setIsEnabled(request.getIsEnabled());
        }
        if (request.getCooldownSeconds() != null) {
            config.setCooldownSeconds(request.getCooldownSeconds());
        }

        aiGroupConfigMapper.updateById(config);
        log.info("群 AI 配置修改成功: groupId={}, configId={}, userId={}", groupId, configId, userId);
    }

    /**
     * 删除群 AI 配置
     */
    @Transactional
    public void deleteGroupConfig(final Long configId, final Long userId) {
        final AiGroupConfig config = aiGroupConfigMapper.selectById(configId);
        if (config == null) {
            return;
        }
        aiGroupConfigMapper.deleteById(configId);
        log.info("群 AI 配置删除成功: configId={}, userId={}", configId, userId);
    }

    /**
     * 检查是否触发 AI
     * 触发规则：关键词匹配 / 概率触发 / @触发
     */
    public boolean checkTrigger(final Long groupId, final String content, final Long aiId) {
        final LambdaQueryWrapper<AiGroupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGroupConfig::getGroupId, groupId)
                .eq(AiGroupConfig::getAiId, aiId)
                .eq(AiGroupConfig::getIsEnabled, true);

        final AiGroupConfig config = aiGroupConfigMapper.selectOne(wrapper);
        if (config == null) {
            return false;
        }

        // 检查冷却时间
        if (isInCooldown(groupId, aiId, config.getCooldownSeconds())) {
            log.debug("AI 冷却中: groupId={}, aiId={}", groupId, aiId);
            return false;
        }

        // 1. @触发（优先级最高）- 云端模式下检查，本地模式跳过（无AI名称数据）
        if (cloudModeEnabled) {
            final AiProfile profile = aiService.getAiProfileById(aiId);
            if (content.contains("@") && content.contains(profile.getName())) {
                setCooldown(groupId, aiId, config.getCooldownSeconds());
                return true;
            }
        } else {
            // 本地模式：客户端自行处理@触发，服务端只检查关键词和概率
            log.debug("本地模式跳过@触发检查: groupId={}, aiId={}", groupId, aiId);
        }

        // 2. 关键词触发
        if (config.getTriggerKeywords() != null && !config.getTriggerKeywords().isEmpty()) {
            final String[] keywords = config.getTriggerKeywords().split(",");
            for (final String keyword : keywords) {
                if (content.contains(keyword.trim())) {
                    setCooldown(groupId, aiId, config.getCooldownSeconds());
                    return true;
                }
            }
        }

        // 3. 概率触发
        if (config.getTriggerProbability() != null && config.getTriggerProbability() > 0) {
            final double random = Math.random();
            if (random < config.getTriggerProbability()) {
                setCooldown(groupId, aiId, config.getCooldownSeconds());
                return true;
            }
        }

        return false;
    }

    /**
     * 获取群所有启用的 AI 配置
     */
    public List<AiGroupConfig> getEnabledConfigs(final Long groupId) {
        final LambdaQueryWrapper<AiGroupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGroupConfig::getGroupId, groupId)
                .eq(AiGroupConfig::getIsEnabled, true);
        return aiGroupConfigMapper.selectList(wrapper);
    }

    /**
     * 检查是否在冷却中
     */
    private boolean isInCooldown(final Long groupId, final Long aiId, final int cooldownSeconds) {
        // H2 模式下 Redis 不可用，跳过冷却检查
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

    /**
     * 设置冷却时间
     */
    private void setCooldown(final Long groupId, final Long aiId, final int cooldownSeconds) {
        // H2 模式下 Redis 不可用，跳过
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