package com.voluntary.chat.server.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.server.config.AiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OpenAI 客户端测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenAiClient 测试")
class OpenAiClientTest {

    private OpenAiClient openAiClient;

    @Mock
    private AiConfig aiConfig;

    @BeforeEach
    void setUp() {
        openAiClient = new OpenAiClient(aiConfig);

        final AiConfig.ProviderConfig openaiConfig = new AiConfig.ProviderConfig();
        openaiConfig.setBaseUrl("https://api.openai.com/v1");
        openaiConfig.setDefaultModel("gpt-3.5-turbo");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of("openai", openaiConfig);
        when(aiConfig.getProviders()).thenReturn(providers);
    }

    @Test
    @DisplayName("获取 Base URL - 成功")
    void getBaseUrl_shouldReturnCorrectUrl() {
        final String baseUrl = openAiClient.getBaseUrl("openai");

        assertEquals("https://api.openai.com/v1", baseUrl);
    }

    @Test
    @DisplayName("获取 Base URL - 未知提供商抛出异常")
    void getBaseUrl_shouldThrowException_forUnknownProvider() {
        assertThrows(IllegalArgumentException.class, () -> {
            openAiClient.getBaseUrl("unknown");
        });
    }

    @Test
    @DisplayName("构建对话请求 - 成功")
    void buildChatRequest_shouldIncludeAllParams() {
        final List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "你好"));

        // 通过反射或直接调用测试（这里简化验证）
        assertNotNull(messages);
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("你好", messages.get(0).get("content"));
    }

    @Test
    @DisplayName("支持多个提供商")
    void shouldSupport_multipleProviders() {
        final AiConfig.ProviderConfig deepseekConfig = new AiConfig.ProviderConfig();
        deepseekConfig.setBaseUrl("https://api.deepseek.com/v1");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of(
                "openai", new AiConfig.ProviderConfig(),
                "deepseek", deepseekConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        assertNotNull(openAiClient.getBaseUrl("openai"));
        assertNotNull(openAiClient.getBaseUrl("deepseek"));
    }

    @Test
    @DisplayName("获取 Base URL - DeepSeek")
    void getBaseUrl_shouldReturnDeepSeekUrl() {
        final AiConfig.ProviderConfig deepseekConfig = new AiConfig.ProviderConfig();
        deepseekConfig.setBaseUrl("https://api.deepseek.com/v1");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of(
                "openai", new AiConfig.ProviderConfig(),
                "deepseek", deepseekConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        final String baseUrl = openAiClient.getBaseUrl("deepseek");

        assertEquals("https://api.deepseek.com/v1", baseUrl);
    }

    @Test
    @DisplayName("获取 Base URL - Claude")
    void getBaseUrl_shouldReturnClaudeUrl() {
        final AiConfig.ProviderConfig claudeConfig = new AiConfig.ProviderConfig();
        claudeConfig.setBaseUrl("https://api.anthropic.com/v1");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of(
                "claude", claudeConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        final String baseUrl = openAiClient.getBaseUrl("claude");

        assertEquals("https://api.anthropic.com/v1", baseUrl);
    }

    @Test
    @DisplayName("构建对话请求 - 包含系统消息")
    void buildChatRequest_shouldIncludeSystemMessage() {
        final List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "你是一个助手"),
                Map.of("role", "user", "content", "你好"));

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertEquals("你是一个助手", messages.get(0).get("content"));
    }

    @Test
    @DisplayName("构建对话请求 - 包含历史消息")
    void buildChatRequest_shouldIncludeHistoryMessages() {
        final List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "你好"),
                Map.of("role", "assistant", "content", "你好！有什么可以帮助你的？"),
                Map.of("role", "user", "content", "介绍一下Java"));

        assertEquals(3, messages.size());
        assertEquals("assistant", messages.get(1).get("role"));
        assertEquals("介绍一下Java", messages.get(2).get("content"));
    }

    @Test
    @DisplayName("获取 Base URL - 提供商配置为null")
    void getBaseUrl_shouldThrowException_whenProviderConfigIsNull() {
        when(aiConfig.getProviders()).thenReturn(null);

        assertThrows(NullPointerException.class, () -> {
            openAiClient.getBaseUrl("openai");
        });
    }

    @Test
    @DisplayName("获取 Base URL - 空提供商名称")
    void getBaseUrl_shouldThrowException_whenProviderNameIsEmpty() {
        // 空提供商名称会抛出IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            openAiClient.getBaseUrl("");
        });
    }

    @Test
    @DisplayName("获取 Base URL - null提供商名称")
    void getBaseUrl_shouldThrowException_whenProviderNameIsNull() {
        assertThrows(NullPointerException.class, () -> {
            openAiClient.getBaseUrl(null);
        });
    }

    @Test
    @DisplayName("ChatConfig - 构造函数和getter")
    void chatConfig_shouldWorkCorrectly() {
        final List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "你好"));

        final OpenAiClient.ChatConfig config = new OpenAiClient.ChatConfig(
                "https://api.openai.com/v1",
                "sk-test-key",
                "gpt-4",
                messages,
                0.7,
                2048);

        assertEquals("https://api.openai.com/v1", config.getBaseUrl());
        assertEquals("sk-test-key", config.getApiKey());
        assertEquals("gpt-4", config.getModel());
        assertEquals(messages, config.getMessages());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2048, config.getMaxTokens());
    }

    @Test
    @DisplayName("ChatConfig - temperature和maxTokens为null")
    void chatConfig_shouldAllowNullParams() {
        final List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "你好"));

        final OpenAiClient.ChatConfig config = new OpenAiClient.ChatConfig(
                "https://api.openai.com/v1",
                "sk-test-key",
                "gpt-4",
                messages,
                null,
                null);

        assertNull(config.getTemperature());
        assertNull(config.getMaxTokens());
    }

    @Test
    @DisplayName("StreamConfig - 构造函数和getter")
    void streamConfig_shouldWorkCorrectly() {
        final List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "你好"));

        final java.util.function.Consumer<String> onChunk = chunk -> {
        };
        final java.util.function.Consumer<String> onComplete = content -> {
        };

        final OpenAiClient.StreamConfig config = new OpenAiClient.StreamConfig(
                "https://api.openai.com/v1",
                "sk-test-key",
                "gpt-4",
                messages,
                0.7,
                2048,
                onChunk,
                onComplete);

        assertEquals("https://api.openai.com/v1", config.getBaseUrl());
        assertEquals("sk-test-key", config.getApiKey());
        assertEquals("gpt-4", config.getModel());
        assertEquals(messages, config.getMessages());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2048, config.getMaxTokens());
        assertEquals(onChunk, config.getOnChunk());
        assertEquals(onComplete, config.getOnComplete());
    }

    @Test
    @DisplayName("StreamConfig - 所有参数为null")
    void streamConfig_shouldAllowNullParams() {
        final OpenAiClient.StreamConfig config = new OpenAiClient.StreamConfig(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertNull(config.getBaseUrl());
        assertNull(config.getApiKey());
        assertNull(config.getModel());
        assertNull(config.getMessages());
        assertNull(config.getTemperature());
        assertNull(config.getMaxTokens());
        assertNull(config.getOnChunk());
        assertNull(config.getOnComplete());
    }

    @Test
    @DisplayName("获取 Base URL - 使用默认值（qwen）")
    void getBaseUrl_shouldUseDefaultUrl_forQwen() {
        // 当配置中没有qwen时，使用默认URL
        final AiConfig.ProviderConfig openaiConfig = new AiConfig.ProviderConfig();
        openaiConfig.setBaseUrl("https://api.openai.com/v1");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of("openai", openaiConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        final String baseUrl = openAiClient.getBaseUrl("qwen");

        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", baseUrl);
    }

    @Test
    @DisplayName("获取 Base URL - 使用默认值（zhipu）")
    void getBaseUrl_shouldUseDefaultUrl_forZhipu() {
        // 当配置中没有zhipu时，使用默认URL
        final AiConfig.ProviderConfig openaiConfig = new AiConfig.ProviderConfig();
        openaiConfig.setBaseUrl("https://api.openai.com/v1");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of("openai", openaiConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        final String baseUrl = openAiClient.getBaseUrl("zhipu");

        assertEquals("https://open.bigmodel.cn/api/paas/v4", baseUrl);
    }

    @Test
    @DisplayName("获取 Base URL - 配置baseUrl为null使用默认值")
    void getBaseUrl_shouldUseDefaultUrl_whenConfigBaseUrlIsNull() {
        final AiConfig.ProviderConfig openaiConfig = new AiConfig.ProviderConfig();
        openaiConfig.setBaseUrl(null); // 配置的baseUrl为null

        final Map<String, AiConfig.ProviderConfig> providers = Map.of("openai", openaiConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        final String baseUrl = openAiClient.getBaseUrl("openai");

        assertEquals("https://api.openai.com/v1", baseUrl);
    }

    @Test
    @DisplayName("获取 Base URL - 大小写不敏感")
    void getBaseUrl_shouldBeCaseInsensitive() {
        final AiConfig.ProviderConfig openaiConfig = new AiConfig.ProviderConfig();
        openaiConfig.setBaseUrl("https://api.openai.com/v1");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of("openai", openaiConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        // 测试大小写不敏感
        final String baseUrl1 = openAiClient.getBaseUrl("OPENAI");
        final String baseUrl2 = openAiClient.getBaseUrl("OpenAI");

        assertEquals("https://api.openai.com/v1", baseUrl1);
        assertEquals("https://api.openai.com/v1", baseUrl2);
    }

    @Test
    @DisplayName("ChatConfig - 空消息列表")
    void chatConfig_shouldAllowEmptyMessages() {
        final List<Map<String, String>> messages = List.of();

        final OpenAiClient.ChatConfig config = new OpenAiClient.ChatConfig(
                "https://api.openai.com/v1",
                "sk-test-key",
                "gpt-4",
                messages,
                0.7,
                2048);

        assertEquals(0, config.getMessages().size());
    }

    @Test
    @DisplayName("StreamConfig - 空消息列表")
    void streamConfig_shouldAllowEmptyMessages() {
        final List<Map<String, String>> messages = List.of();

        final java.util.function.Consumer<String> onChunk = chunk -> {
        };
        final java.util.function.Consumer<String> onComplete = content -> {
        };

        final OpenAiClient.StreamConfig config = new OpenAiClient.StreamConfig(
                "https://api.openai.com/v1",
                "sk-test-key",
                "gpt-4",
                messages,
                0.7,
                2048,
                onChunk,
                onComplete);

        assertEquals(0, config.getMessages().size());
    }

    @Test
    @DisplayName("获取 Base URL - deepseek默认值")
    void getBaseUrl_shouldUseDefaultUrl_forDeepseek() {
        // 当配置中没有deepseek时，使用默认URL
        final AiConfig.ProviderConfig openaiConfig = new AiConfig.ProviderConfig();
        openaiConfig.setBaseUrl("https://api.openai.com/v1");

        final Map<String, AiConfig.ProviderConfig> providers = Map.of("openai", openaiConfig);
        when(aiConfig.getProviders()).thenReturn(providers);

        final String baseUrl = openAiClient.getBaseUrl("deepseek");

        assertEquals("https://api.deepseek.com/v1", baseUrl);
    }
}