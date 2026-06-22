package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.server.dto.request.AiGroupConfigRequest;
import com.voluntary.chat.server.dto.response.AiGroupConfigResponse;
import com.voluntary.chat.server.entity.AiGroupConfig;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.mapper.AiGroupConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 群配置服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiGroupConfigService 测试")
class AiGroupConfigServiceTest {

    private AiGroupConfigService aiGroupConfigService;

    @Mock
    private AiGroupConfigMapper aiGroupConfigMapper;

    @Mock
    private AiService aiService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final Long GROUP_ID = 2001L;
    private static final Long USER_ID = 1001L;
    private static final Long AI_ID = 3001L;
    private static final Long CONFIG_ID = 4001L;

    @BeforeEach
    void setUp() {
        aiGroupConfigService = new AiGroupConfigService(aiGroupConfigMapper, aiService, redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("创建群 AI 配置 - 成功")
    void createGroupConfig_shouldSucceed() {
        final AiProfile profile = createAiProfile();
        final AiGroupConfigRequest request = new AiGroupConfigRequest();
        request.setAiId(AI_ID);
        request.setTriggerKeywords("小助手,AI");
        request.setTriggerProbability(0.1);
        request.setIsEnabled(true);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(aiGroupConfigMapper.insert(any(AiGroupConfig.class))).thenAnswer(invocation -> {
            final AiGroupConfig config = invocation.getArgument(0);
            config.setId(CONFIG_ID);
            return 1;
        });

        final Long configId = aiGroupConfigService.createGroupConfig(GROUP_ID, USER_ID, request);

        assertEquals(CONFIG_ID, configId);
        verify(aiGroupConfigMapper).insert(any(AiGroupConfig.class));
    }

    @Test
    @DisplayName("创建群 AI 配置 - 已存在")
    void createGroupConfig_shouldFail_whenExists() {
        final AiProfile profile = createAiProfile();
        final AiGroupConfig existing = new AiGroupConfig();
        existing.setId(CONFIG_ID);

        final AiGroupConfigRequest request = new AiGroupConfigRequest();
        request.setAiId(AI_ID);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThrows(BusinessException.class, () -> {
            aiGroupConfigService.createGroupConfig(GROUP_ID, USER_ID, request);
        });

        verify(aiGroupConfigMapper, never()).insert(any(AiGroupConfig.class));
    }

    @Test
    @DisplayName("获取群 AI 配置列表 - 成功")
    void getGroupConfigs_shouldReturnList() {
        final AiGroupConfig config1 = createConfig(CONFIG_ID, AI_ID);
        final AiGroupConfig config2 = createConfig(CONFIG_ID + 1, AI_ID + 1);
        final AiProfile profile1 = createAiProfile();
        final AiProfile profile2 = createAiProfile();
        profile2.setId(AI_ID + 1);
        profile2.setName("技术助手");

        when(aiGroupConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(config1, config2));
        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile1);
        when(aiService.getAiProfileById(AI_ID + 1)).thenReturn(profile2);

        final List<AiGroupConfigResponse> configs = aiGroupConfigService.getGroupConfigs(GROUP_ID);

        assertEquals(2, configs.size());
        assertEquals("小助手", configs.get(0).getAiName());
        assertEquals("技术助手", configs.get(1).getAiName());
    }

    @Test
    @DisplayName("检查触发 - 关键词匹配")
    void checkTrigger_shouldTrigger_withKeyword() {
        final AiGroupConfig config = createConfig(CONFIG_ID, AI_ID);
        config.setTriggerKeywords("小助手,AI");
        config.setCooldownSeconds(30);

        final AiProfile profile = createAiProfile();

        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(valueOperations.get(anyString())).thenReturn(null); // 无冷却

        final boolean triggered = aiGroupConfigService.checkTrigger(GROUP_ID, "小助手你好", AI_ID);

        assertTrue(triggered);
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("检查触发 - 冷却中不触发")
    void checkTrigger_shouldNotTrigger_whenCooldown() {
        final AiGroupConfig config = createConfig(CONFIG_ID, AI_ID);
        config.setCooldownSeconds(30);

        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(System.currentTimeMillis()));

        final boolean triggered = aiGroupConfigService.checkTrigger(GROUP_ID, "小助手", AI_ID);

        assertFalse(triggered);
    }

    @Test
    @DisplayName("检查触发 - @触发")
    void checkTrigger_shouldTrigger_withAt() {
        final AiGroupConfig config = createConfig(CONFIG_ID, AI_ID);
        config.setCooldownSeconds(30);

        final AiProfile profile = createAiProfile();
        profile.setName("小助手");

        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(valueOperations.get(anyString())).thenReturn(null);

        final boolean triggered = aiGroupConfigService.checkTrigger(GROUP_ID, "@小助手 请回答", AI_ID);

        assertTrue(triggered);
    }

    @Test
    @DisplayName("检查触发 - 概率触发")
    void checkTrigger_shouldTrigger_withProbability() {
        final AiGroupConfig config = createConfig(CONFIG_ID, AI_ID);
        config.setTriggerProbability(1.0); // 100% 触发
        config.setCooldownSeconds(30);

        final AiProfile profile = createAiProfile();

        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(valueOperations.get(anyString())).thenReturn(null);

        final boolean triggered = aiGroupConfigService.checkTrigger(GROUP_ID, "普通消息", AI_ID);

        assertTrue(triggered);
    }

    @Test
    @DisplayName("检查触发 - 配置不存在")
    void checkTrigger_shouldNotTrigger_whenConfigNotFound() {
        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        final boolean triggered = aiGroupConfigService.checkTrigger(GROUP_ID, "小助手", AI_ID);

        assertFalse(triggered);
    }

    @Test
    @DisplayName("检查触发 - 配置已禁用")
    void checkTrigger_shouldNotTrigger_whenDisabled() {
        final AiGroupConfig config = createConfig(CONFIG_ID, AI_ID);
        config.setIsEnabled(false);

        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        final boolean triggered = aiGroupConfigService.checkTrigger(GROUP_ID, "小助手", AI_ID);

        assertFalse(triggered);
    }

    @Test
    @DisplayName("检查触发 - 无关键词匹配")
    void checkTrigger_shouldNotTrigger_withoutKeywordMatch() {
        final AiGroupConfig config = createConfig(CONFIG_ID, AI_ID);
        config.setTriggerKeywords("AI,机器人");
        config.setTriggerProbability(0.0); // 0% 概率触发
        config.setCooldownSeconds(30);

        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
        when(valueOperations.get(anyString())).thenReturn(null);

        final boolean triggered = aiGroupConfigService.checkTrigger(GROUP_ID, "普通消息", AI_ID);

        assertFalse(triggered);
    }

    @Test
    @DisplayName("获取群 AI 配置列表 - 空列表")
    void getGroupConfigs_shouldReturnEmptyList() {
        when(aiGroupConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        final List<AiGroupConfigResponse> configs = aiGroupConfigService.getGroupConfigs(GROUP_ID);

        assertEquals(0, configs.size());
    }

    @Test
    @DisplayName("创建群 AI 配置 - 使用默认值")
    void createGroupConfig_shouldUseDefaults() {
        final AiProfile profile = createAiProfile();
        final AiGroupConfigRequest request = new AiGroupConfigRequest();
        request.setAiId(AI_ID);
        // 其他字段为null，使用默认值

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiGroupConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(aiGroupConfigMapper.insert(any(AiGroupConfig.class))).thenAnswer(invocation -> {
            final AiGroupConfig config = invocation.getArgument(0);
            config.setId(CONFIG_ID);
            return 1;
        });

        final Long configId = aiGroupConfigService.createGroupConfig(GROUP_ID, USER_ID, request);

        assertEquals(CONFIG_ID, configId);

        final ArgumentCaptor<AiGroupConfig> captor = ArgumentCaptor.forClass(AiGroupConfig.class);
        verify(aiGroupConfigMapper).insert(captor.capture());

        final AiGroupConfig savedConfig = captor.getValue();
        assertEquals(0.0, savedConfig.getTriggerProbability()); // 默认值
        assertTrue(savedConfig.getIsEnabled()); // 默认值
        assertEquals(30, savedConfig.getCooldownSeconds()); // 默认值
    }

    private AiProfile createAiProfile() {
        final AiProfile profile = new AiProfile();
        profile.setId(AI_ID);
        profile.setName("小助手");
        return profile;
    }

    private AiGroupConfig createConfig(final Long configId, final Long aiId) {
        final AiGroupConfig config = new AiGroupConfig();
        config.setId(configId);
        config.setGroupId(GROUP_ID);
        config.setAiId(aiId);
        config.setIsEnabled(true);
        config.setCooldownSeconds(30);
        return config;
    }
}