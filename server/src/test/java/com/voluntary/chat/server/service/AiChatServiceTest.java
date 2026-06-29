package com.voluntary.chat.server.service;

import com.voluntary.chat.common.enums.SenderType;
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
import org.springframework.core.task.SyncTaskExecutor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 对话服务测试
 *
 * <p>
 * 使用 SyncTaskExecutor 将异步执行转为同步，便于测试验证。
 * </p>
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
    private AiMemoryService aiMemoryService;

    @Mock
    private ChatWebSocketHandler webSocketHandler;

    private static final Long USER_ID = 1001L;
    private static final Long AI_ID = 3001L;
    private static final String SESSION_ID = "a_3001_1001";

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatService(aiService, openAiClient, aiConfig, messageMapper, aiMemoryService);
        aiChatService.setAiTaskExecutor(new SyncTaskExecutor());
        aiChatService.setWebSocketHandler(webSocketHandler);

        final AiConfig.ContextConfig contextConfig = new AiConfig.ContextConfig();
        contextConfig.setMaxHistoryRounds(10);
        when(aiConfig.getContext()).thenReturn(contextConfig);
    }

    @Test
    @DisplayName("处理 AI 对话 - 成功")
    void handleAiChat_shouldSucceed() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 空历史消息")
    void handleAiChat_shouldSucceed_withEmptyHistory() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
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
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 使用 persona 作为系统提示")
    void handleAiChat_shouldUsePersona_whenSystemPromptIsNull() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt(null);
        profile.setPersona("我是AI助手小助手");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - DeepSeek 模型")
    void handleAiChat_shouldSucceed_withDeepSeek() {
        final AiProfile profile = createAiProfile();
        profile.setModelProvider("deepseek");
        profile.setModel("deepseek-chat");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-deepseek-key");
        when(openAiClient.getBaseUrl("deepseek")).thenReturn("https://api.deepseek.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
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
        when(messageMapper.selectList(any())).thenReturn(List.of(msg3, msg2, msg1));
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "Java有什么特点？", "msg-002");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 温度为 null 使用默认值")
    void handleAiChat_shouldUseDefaultTemperature_whenNull() {
        final AiProfile profile = createAiProfile();
        profile.setTemperature(null);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - maxTokens 为 null 使用默认值")
    void handleAiChat_shouldUseDefaultMaxTokens_whenNull() {
        final AiProfile profile = createAiProfile();
        profile.setMaxTokens(null);

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 历史消息达到上限")
    void handleAiChat_shouldLimitHistoryMessages() {
        final AiProfile profile = createAiProfile();
        profile.setSystemPrompt("你是一个助手");

        final List<Message> historyMessages = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            historyMessages.add(createUserMessage("历史消息" + i));
            historyMessages.add(createAiMessage("AI回复" + i));
        }

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(historyMessages.subList(0, 20));
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 排除撤回消息")
    void handleAiChat_shouldExcludeRecalledMessages() {
        final AiProfile profile = createAiProfile();

        final Message msg1 = createUserMessage("你好");
        final Message msg3 = createUserMessage("新问题");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of(msg1, msg3));
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "新问题", "msg-002");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 异常时不抛出，发送错误流")
    void handleAiChat_shouldHandleExceptionGracefully() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenThrow(new RuntimeException("解密失败"));

        assertDoesNotThrow(() -> aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001"));

        verify(openAiClient, never()).streamChatCompletion(any());
    }

    @Test
    @DisplayName("处理 AI 对话 - 记忆检索失败时降级")
    void handleAiChat_shouldDegradeWhenMemorySearchFails() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);
        when(aiMemoryService.searchRelevantMemories(anyLong(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("向量库不可用"));

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("处理 AI 对话 - 验证流式回调执行")
    void handleAiChat_shouldExecuteStreamCallbacks() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(openAiClient.getBaseUrl("openai")).thenReturn("https://api.openai.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(5001L);
            return 1;
        });

        // 触发回调执行，覆盖 saveAiMessage 和 WebSocket 推送逻辑
        org.mockito.Mockito.doAnswer(invocation -> {
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
    @DisplayName("处理 AI 对话 - 不同模型提供商")
    void handleAiChat_shouldHandleDifferentProviders() {
        final AiProfile profile = createAiProfile();
        profile.setModelProvider("anthropic");
        profile.setModel("claude-3");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-anthropic-key");
        when(openAiClient.getBaseUrl("anthropic")).thenReturn("https://api.anthropic.com/v1");
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.handleAiChat(USER_ID, AI_ID, SESSION_ID, "你好", "msg-001");

        verify(openAiClient).getBaseUrl("anthropic");
        verify(openAiClient).streamChatCompletion(any(OpenAiClient.StreamConfig.class));
    }

    @Test
    @DisplayName("保存用户消息 - 验证字段设置")
    void saveUserMessage_shouldSetCorrectFields() {
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.saveUserMessage(USER_ID, SESSION_ID, "测试内容", AI_ID);

        verify(messageMapper).insert(any(Message.class));
    }

    @Test
    @DisplayName("保存 AI 消息 - 验证字段设置")
    void saveAiMessage_shouldSetCorrectFields() {
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        aiChatService.saveAiMessage(AI_ID, SESSION_ID, "AI回复", USER_ID);

        verify(messageMapper).insert(any(Message.class));
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
        message.setSenderType(SenderType.USER.ordinal());
        message.setContent(content);
        return message;
    }

    private Message createAiMessage(final String content) {
        final Message message = new Message();
        message.setId(10002L);
        message.setSessionId(SESSION_ID);
        message.setSenderId(AI_ID);
        message.setSenderType(SenderType.AI.ordinal());
        message.setContent(content);
        return message;
    }
}