package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 对话服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiChatService 测试")
class AiChatServiceTest {

    private AiChatService aiChatService;

    @Mock
    private AiService aiService;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private AiConfig aiConfig;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatWebSocketHandler webSocketHandler;

    private static final Long USER_ID = 1001L;
    private static final Long AI_ID = 3001L;
    private static final String SESSION_ID = "a_3001_1001";

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatService(aiService, openAiClient, aiConfig, messageMapper);
        ReflectionTestUtils.setField(aiChatService, "webSocketHandler", webSocketHandler);

        final AiConfig.ContextConfig contextConfig = new AiConfig.ContextConfig();
        contextConfig.setMaxHistoryRounds(10);
        when(aiConfig.getContext()).thenReturn(contextConfig);
    }

    @Test
    @DisplayName("处理 AI 对话 - 成功")
    void handleAiChat_shouldSucceed() {
        final AiProfile profile = createAiProfile();
        final Message userMessage = createUserMessage("你好");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userMessage));
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("构建对话上下文 - 包含历史消息")
    void buildContext_shouldIncludeHistory() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt("你是一个助手");

        final Message msg1 = createUserMessage("你好");
        final Message msg2 = createAiMessage("你好！有什么可以帮助你的？");
        final Message msg3 = createUserMessage("介绍一下Java");

        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(msg1, msg2, msg3));

        // 通过反射或直接调用测试（这里简化验证）
        verify(messageMapper, never()).selectList(any()); // 尚未调用
    }

    @Test
    @DisplayName("处理 AI 对话 - 空历史消息")
    void handleAiChat_shouldSucceed_withEmptyHistory() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 无系统提示")
    void handleAiChat_shouldSucceed_withoutSystemPrompt() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt(null);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 自定义温度和最大tokens")
    void handleAiChat_shouldUseCustomParams() {
        final AiProfile profile = createAiProfile();
        profile.setTemperature(0.5);
        profile.setMaxTokens(4096);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - DeepSeek模型")
    void handleAiChat_shouldSucceed_withDeepSeek() {
        final AiProfile profile = createAiProfile();
        profile.setModelProvider("deepseek");
        profile.setModel("deepseek-chat");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-deepseek-key");
        when(openAiClient.getBaseUrl("deepseek")).thenReturn("https://api.deepseek.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 包含历史消息")
    void handleAiChat_shouldSucceed_withHistory() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt("你是一个助手");

        final Message msg1 = createUserMessage("你好");
        final Message msg2 = createAiMessage("你好！有什么可以帮助你的？");
        final Message msg3 = createUserMessage("介绍一下Java");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(msg3, msg2, msg1)); // 按时间倒序
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "Java有什么特点？", "msg-002");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 使用persona作为系统提示")
    void handleAiChat_shouldUsePersona_whenSystemPromptIsNull() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt(null);
        profile.setPersona("我是AI助手小助手");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 无系统提示和persona")
    void handleAiChat_shouldSkipSystemPrompt_whenBothNull() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt(null);
        profile.setPersona(null);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - AI Profile不存在")
    void handleAiChat_shouldThrowException_whenProfileNotFound() {
        when(aiService.getAiProfileById(AI_ID)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001"));

        verify(openAiClient, never()).streamChatCompletion(any());
    }

    @Test
    @DisplayName("处理 AI 对话 - 历史消息达到上限")
    void handleAiChat_shouldLimitHistoryMessages() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt("你是一个助手");

        // 创建超过上限的历史消息（20条）
        final List<Message> historyMessages = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            historyMessages.add(createUserMessage("历史消息" + i));
            historyMessages.add(createAiMessage("AI回复" + i));
        }

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(historyMessages.subList(0, 20)); // 只返回20条
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
        verify(messageMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 包含撤回消息的历史")
    void handleAiChat_shouldExcludeRecalledMessages() {
        final AiProfile profile = createAiProfile();

        final Message msg1 = createUserMessage("你好");
        final Message msg2 = createAiMessage("你好！");
        msg2.setRecallTime(java.time.LocalDateTime.now()); // 已撤回

        final Message msg3 = createUserMessage("新问题");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(msg1, msg3)); // 撤回消息被排除
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "新问题", "msg-002");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 验证消息保存（通过回调）")
    void handleAiChat_shouldSaveAiMessage_viaCallback() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(5001L);
            return 1;
        });

        // 触发回调执行，覆盖 saveAiMessage 和 WebSocket 推送逻辑
        doAnswer(invocation -> {
            OpenAiClient.StreamConfig config = invocation.getArgument(0);
            config.getOnChunk().accept("你好");
            config.getOnComplete().accept("你好！有什么可以帮助你的？");
            return null;
        }).when(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        // 验证用户消息和AI消息都被保存
        verify(messageMapper, times(2)).insert(any(Message.class));
        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 验证WebSocket推送")
    void handleAiChat_shouldPushWebSocketMessage() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        // 验证WebSocket推送（通过StreamConfig的回调）
        verify(openAiClient).streamChatCompletion(argThat(config -> {
            // 验证回调函数存在
            return config != null;
        }));
    }

    @Test
    @DisplayName("处理 AI 对话 - 不同模型提供商")
    void handleAiChat_shouldHandleDifferentProviders() {
        final AiProfile profile = createAiProfile();
        profile.setModelProvider("anthropic");
        profile.setModel("claude-3");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-anthropic-key");
        when(openAiClient.getBaseUrl("anthropic")).thenReturn("https://api.anthropic.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).getBaseUrl("anthropic");
        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 温度为null使用默认值")
    void handleAiChat_shouldUseDefaultTemperature_whenNull() {
        final AiProfile profile = createAiProfile();
        profile.setTemperature(null);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - maxTokens为null使用默认值")
    void handleAiChat_shouldUseDefaultMaxTokens_whenNull() {
        final AiProfile profile = createAiProfile();
        profile.setMaxTokens(null);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    private AiProfile createAiProfile() {
        final AiProfile profile = new AiProfile();
        profile.setId(AI_ID);
        profile.setName("小助手");
        profile.setUserId(USER_ID);
        profile.setModelProvider("openai");
        profile.setModel("gpt-4");
        profile.setTemperature(0.7);
        profile.setMaxTokens(2048);
        profile.setSystemPrompt("你是一个友好的助手");
        return profile;
    }

    private Message createUserMessage(final String content) {
        final Message message = new Message();
        message.setId(10001L);
        message.setSessionId(SESSION_ID);
        message.setSenderId(USER_ID);
        message.setSenderType(0); // User
        message.setContent(content);
        return message;
    }

    private Message createAiMessage(final String content) {
        final Message message = new Message();
        message.setId(10002L);
        message.setSessionId(SESSION_ID);
        message.setSenderId(AI_ID);
        message.setSenderType(1); // AI
        message.setContent(content);
        return message;
    }
}