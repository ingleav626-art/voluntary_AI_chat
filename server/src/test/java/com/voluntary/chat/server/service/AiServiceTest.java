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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 角色管理服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiService 测试")
class AiServiceTest {

    private AiService aiService;

    @Mock
    private AiProfileMapper aiProfileMapper;

    @Mock
    private AiConfig aiConfig;

    private static final Long USER_ID = 1001L;
    private static final Long AI_ID = 3001L;
    private static final String ENCRYPTION_KEY = "default-key-for-dev-only-32b!";

    @BeforeEach
    void setUp() {
        aiService = new AiService(aiProfileMapper, aiConfig);
        when(aiConfig.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
    }

    @Test
    @DisplayName("创建 AI 角色 - 成功")
    void createAiProfile_shouldSucceed() {
        final CreateAiProfileRequest request = new CreateAiProfileRequest();
        request.setName("小助手");
        request.setPersona("你是一个友好的助手");
        request.setModelProvider("openai");
        request.setModel("gpt-4");
        request.setApiKey("sk-test-key");

        when(aiProfileMapper.insert(any(AiProfile.class))).thenAnswer(invocation -> {
            final AiProfile profile = invocation.getArgument(0);
            profile.setId(AI_ID);
            return 1;
        });

        final Long aiId = aiService.createAiProfile(USER_ID, request);

        assertEquals(AI_ID, aiId);

        final ArgumentCaptor<AiProfile> captor = ArgumentCaptor.forClass(AiProfile.class);
        verify(aiProfileMapper).insert(captor.capture());

        final AiProfile savedProfile = captor.getValue();
        assertEquals(USER_ID, savedProfile.getUserId());
        assertEquals("小助手", savedProfile.getName());
        assertEquals("openai", savedProfile.getModelProvider());
        // API Key 应加密存储
        assertNotNull(savedProfile.getApiKeyEnc());
        assertNotEquals("sk-test-key", savedProfile.getApiKeyEnc());
    }

    @Test
    @DisplayName("获取 AI 列表 - 成功")
    void listAiProfiles_shouldReturnList() {
        final AiProfile profile1 = createAiProfile(AI_ID, "小助手");
        final AiProfile profile2 = createAiProfile(AI_ID + 1, "技术助手");

        final Page<AiProfile> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(profile1, profile2));
        pageResult.setTotal(2);

        when(aiProfileMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        final PageResult<AiProfileResponse> result = aiService.listAiProfiles(USER_ID, 1, 20);

        assertEquals(2, result.getList().size());
        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getSize());
    }

    @Test
    @DisplayName("修改 AI 角色 - 成功")
    void updateAiProfile_shouldSucceed() {
        final AiProfile existing = createAiProfile(AI_ID, "小助手");
        existing.setUserId(USER_ID);

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(existing);
        when(aiProfileMapper.updateById(any(AiProfile.class))).thenReturn(1);

        final UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setName("新助手");
        request.setPersona("新的persona");

        aiService.updateAiProfile(USER_ID, AI_ID, request);

        final ArgumentCaptor<AiProfile> captor = ArgumentCaptor.forClass(AiProfile.class);
        verify(aiProfileMapper).updateById(captor.capture());

        final AiProfile updated = captor.getValue();
        assertEquals("新助手", updated.getName());
        assertEquals("新的persona", updated.getPersona());
    }

    @Test
    @DisplayName("修改 AI 角色 - 无权限")
    void updateAiProfile_shouldFail_withoutPermission() {
        final AiProfile existing = createAiProfile(AI_ID, "小助手");
        existing.setUserId(USER_ID + 1); // 其他用户

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(existing);

        final UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setName("新助手");

        assertThrows(BusinessException.class, () -> {
            aiService.updateAiProfile(USER_ID, AI_ID, request);
        });

        verify(aiProfileMapper, never()).updateById(any(AiProfile.class));
    }

    @Test
    @DisplayName("删除 AI 角色 - 成功")
    void deleteAiProfile_shouldSucceed() {
        final AiProfile existing = createAiProfile(AI_ID, "小助手");
        existing.setUserId(USER_ID);

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(existing);
        when(aiProfileMapper.deleteById(AI_ID)).thenReturn(1);

        aiService.deleteAiProfile(USER_ID, AI_ID);

        verify(aiProfileMapper).deleteById(AI_ID);
    }

    @Test
    @DisplayName("获取 AI 角色 - 成功")
    void getAiProfileById_shouldSucceed() {
        final AiProfile profile = createAiProfile(AI_ID, "小助手");

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(profile);

        final AiProfile result = aiService.getAiProfileById(AI_ID);

        assertNotNull(result);
        assertEquals(AI_ID, result.getId());
        assertEquals("小助手", result.getName());
    }

    @Test
    @DisplayName("获取 AI 角色 - 不存在")
    void getAiProfileById_shouldFail_whenNotFound() {
        when(aiProfileMapper.selectById(AI_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> {
            aiService.getAiProfileById(AI_ID);
        });
    }

    @Test
    @DisplayName("解密 API Key - 成功")
    void decryptApiKey_shouldSucceed() {
        final AiProfile profile = createAiProfile(AI_ID, "小助手");
        profile.setApiKeyEnc(AesKeyUtil.encrypt("sk-test-key", ENCRYPTION_KEY));

        final String decrypted = aiService.decryptApiKey(profile);

        assertEquals("sk-test-key", decrypted);
    }

    @Test
    @DisplayName("创建 AI 角色 - 使用默认值（null字段）")
    void createAiProfile_shouldUseDefaults_withNullFields() {
        when(aiConfig.getDefaultTemperature()).thenReturn(0.7);
        when(aiConfig.getDefaultMaxTokens()).thenReturn(2048);

        final CreateAiProfileRequest request = new CreateAiProfileRequest();
        request.setName("小助手");
        request.setModelProvider("openai");
        request.setModel("gpt-4");
        request.setApiKey("sk-test-key");
        // isGroup, temperature, maxTokens 都是 null

        when(aiProfileMapper.insert(any(AiProfile.class))).thenAnswer(invocation -> {
            final AiProfile profile = invocation.getArgument(0);
            profile.setId(AI_ID);
            return 1;
        });

        final Long aiId = aiService.createAiProfile(USER_ID, request);

        assertEquals(AI_ID, aiId);

        final ArgumentCaptor<AiProfile> captor = ArgumentCaptor.forClass(AiProfile.class);
        verify(aiProfileMapper).insert(captor.capture());

        final AiProfile savedProfile = captor.getValue();
        assertFalse(savedProfile.getIsGroup()); // 默认 false
        assertEquals(0.7, savedProfile.getTemperature()); // 默认值
        assertEquals(2048, savedProfile.getMaxTokens()); // 默认值
    }

    @Test
    @DisplayName("创建 AI 角色 - 提供所有字段")
    void createAiProfile_shouldUseProvidedValues_withAllFields() {
        final CreateAiProfileRequest request = new CreateAiProfileRequest();
        request.setName("群助手");
        request.setPersona("群聊助手");
        request.setModelProvider("deepseek");
        request.setModel("deepseek-chat");
        request.setApiKey("sk-test-key");
        request.setIsGroup(true);
        request.setTemperature(0.5);
        request.setMaxTokens(4096);

        when(aiProfileMapper.insert(any(AiProfile.class))).thenAnswer(invocation -> {
            final AiProfile profile = invocation.getArgument(0);
            profile.setId(AI_ID);
            return 1;
        });

        final Long aiId = aiService.createAiProfile(USER_ID, request);

        final ArgumentCaptor<AiProfile> captor = ArgumentCaptor.forClass(AiProfile.class);
        verify(aiProfileMapper).insert(captor.capture());

        final AiProfile savedProfile = captor.getValue();
        assertTrue(savedProfile.getIsGroup());
        assertEquals(0.5, savedProfile.getTemperature());
        assertEquals(4096, savedProfile.getMaxTokens());
    }

    @Test
    @DisplayName("修改 AI 角色 - 更新所有字段")
    void updateAiProfile_shouldUpdateAllFields() {
        final AiProfile existing = createAiProfile(AI_ID, "小助手");
        existing.setUserId(USER_ID);

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(existing);
        when(aiProfileMapper.updateById(any(AiProfile.class))).thenReturn(1);

        final UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setName("新助手");
        request.setAvatar("http://example.com/avatar.png");
        request.setPersona("新persona");
        request.setSystemPrompt("新system prompt");
        request.setModel("gpt-4-turbo");
        request.setApiKey("sk-new-key");
        request.setIsGroup(true);
        request.setTemperature(0.8);
        request.setMaxTokens(8192);

        aiService.updateAiProfile(USER_ID, AI_ID, request);

        final ArgumentCaptor<AiProfile> captor = ArgumentCaptor.forClass(AiProfile.class);
        verify(aiProfileMapper).updateById(captor.capture());

        final AiProfile updated = captor.getValue();
        assertEquals("新助手", updated.getName());
        assertEquals("http://example.com/avatar.png", updated.getAvatar());
        assertEquals("新persona", updated.getPersona());
        assertEquals("新system prompt", updated.getSystemPrompt());
        assertEquals("gpt-4-turbo", updated.getModel());
        assertNotNull(updated.getApiKeyEnc());
        assertTrue(updated.getIsGroup());
        assertEquals(0.8, updated.getTemperature());
        assertEquals(8192, updated.getMaxTokens());
    }

    @Test
    @DisplayName("删除 AI 角色 - 无权限")
    void deleteAiProfile_shouldFail_withoutPermission() {
        final AiProfile existing = createAiProfile(AI_ID, "小助手");
        existing.setUserId(USER_ID + 1); // 其他用户

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(existing);

        assertThrows(BusinessException.class, () -> {
            aiService.deleteAiProfile(USER_ID, AI_ID);
        });

        verify(aiProfileMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("获取 AI 角色 - 已删除")
    void getAiProfileById_shouldFail_whenDeleted() {
        final AiProfile profile = createAiProfile(AI_ID, "小助手");
        profile.setIsDeleted(1); // 已删除

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(profile);

        assertThrows(BusinessException.class, () -> {
            aiService.getAiProfileById(AI_ID);
        });
    }

    @Test
    @DisplayName("获取 AI 角色 - 已禁用")
    void getAiProfileById_shouldFail_whenDisabled() {
        final AiProfile profile = createAiProfile(AI_ID, "小助手");
        profile.setIsDeleted(1); // 已删除

        when(aiProfileMapper.selectById(AI_ID)).thenReturn(profile);

        assertThrows(BusinessException.class, () -> {
            aiService.getAiProfileById(AI_ID);
        });
    }

    @Test
    @DisplayName("获取 AI 列表 - 空列表")
    void listAiProfiles_shouldReturnEmptyList() {
        final Page<AiProfile> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of());
        pageResult.setTotal(0);

        when(aiProfileMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        final PageResult<AiProfileResponse> result = aiService.listAiProfiles(USER_ID, 1, 20);

        assertEquals(0, result.getList().size());
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("创建 AI 角色 - 加密密钥未配置时抛出异常")
    void createAiProfile_shouldThrowWhenEncryptionKeyMissing() {
        reset(aiConfig);
        when(aiConfig.getEncryptionKey()).thenReturn(null);

        final CreateAiProfileRequest request = new CreateAiProfileRequest();
        request.setName("小助手");
        request.setApiKey("sk-test-key");

        final BusinessException ex = assertThrows(BusinessException.class,
                () -> aiService.createAiProfile(USER_ID, request));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());

        verify(aiProfileMapper, never()).insert(any(AiProfile.class));
    }

    @Test
    @DisplayName("解密 API Key - 加密密钥未配置时抛出异常")
    void decryptApiKey_shouldThrowWhenEncryptionKeyMissing() {
        reset(aiConfig);
        when(aiConfig.getEncryptionKey()).thenReturn("");

        final AiProfile profile = createAiProfile(AI_ID, "小助手");
        profile.setApiKeyEnc("encrypted-data");

        final BusinessException ex = assertThrows(BusinessException.class,
                () -> aiService.decryptApiKey(profile));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }

    private AiProfile createAiProfile(final Long id, final String name) {
        final AiProfile profile = new AiProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setUserId(USER_ID);
        profile.setModelProvider("openai");
        profile.setModel("gpt-4");
        profile.setStatus(0);
        profile.setIsDeleted(0);
        profile.setCreateTime(LocalDateTime.now());
        return profile;
    }
}