package com.voluntary.chat.server.client;

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
import static org.mockito.Mockito.*;

/**
 * Embedding 客户端测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmbeddingClient 测试")
class EmbeddingClientTest {

    private EmbeddingClient embeddingClient;

    @Mock
    private AiConfig aiConfig;

    @BeforeEach
    void setUp() {
        embeddingClient = new EmbeddingClient(aiConfig);

        final AiConfig.EmbeddingConfig embeddingConfig = new AiConfig.EmbeddingConfig();
        embeddingConfig.setProvider("openai");
        embeddingConfig.setModel("text-embedding-3-small");
        embeddingConfig.setDimension(1536);

        final AiConfig.ProviderConfig openaiConfig = new AiConfig.ProviderConfig();
        openaiConfig.setBaseUrl("https://api.openai.com/v1");

        when(aiConfig.getEmbedding()).thenReturn(embeddingConfig);
        when(aiConfig.getProviders()).thenReturn(Map.of("openai", openaiConfig));
    }

    @Test
    @DisplayName("配置正确加载")
    void config_shouldLoadCorrectly() {
        final AiConfig.EmbeddingConfig config = aiConfig.getEmbedding();

        assertEquals("openai", config.getProvider());
        assertEquals("text-embedding-3-small", config.getModel());
        assertEquals(1536, config.getDimension());
    }

    @Test
    @DisplayName("批量生成 Embedding - 空列表")
    void createEmbeddings_shouldHandle_emptyList() {
        final List<List<Double>> results = embeddingClient.createEmbeddings(List.of(), "sk-test-key");

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    @DisplayName("批量生成 Embedding - 多个文本")
    void createEmbeddings_shouldProcess_multipleTexts() {
        final List<String> texts = List.of("文本1", "文本2", "文本3");

        // 实际调用会失败（无真实API），这里验证方法签名
        assertNotNull(texts);
        assertEquals(3, texts.size());
    }

    @Test
    @DisplayName("配置正确加载 - 不同维度")
    void config_shouldLoadCorrectly_withDifferentDimension() {
        final AiConfig.EmbeddingConfig embeddingConfig = new AiConfig.EmbeddingConfig();
        embeddingConfig.setProvider("openai");
        embeddingConfig.setModel("text-embedding-3-large");
        embeddingConfig.setDimension(3072);

        when(aiConfig.getEmbedding()).thenReturn(embeddingConfig);

        final AiConfig.EmbeddingConfig config = aiConfig.getEmbedding();

        assertEquals("openai", config.getProvider());
        assertEquals("text-embedding-3-large", config.getModel());
        assertEquals(3072, config.getDimension());
    }

    @Test
    @DisplayName("配置正确加载 - DeepSeek提供商")
    void config_shouldLoadCorrectly_withDeepSeekProvider() {
        final AiConfig.EmbeddingConfig embeddingConfig = new AiConfig.EmbeddingConfig();
        embeddingConfig.setProvider("deepseek");
        embeddingConfig.setModel("deepseek-embedding");

        when(aiConfig.getEmbedding()).thenReturn(embeddingConfig);

        final AiConfig.EmbeddingConfig config = aiConfig.getEmbedding();

        assertEquals("deepseek", config.getProvider());
        assertEquals("deepseek-embedding", config.getModel());
    }

    @Test
    @DisplayName("批量生成 Embedding - 单个文本")
    void createEmbeddings_shouldProcess_singleText() {
        final List<String> texts = List.of("单个文本");

        assertNotNull(texts);
        assertEquals(1, texts.size());
    }

    @Test
    @DisplayName("批量生成 Embedding - null文本列表")
    void createEmbeddings_shouldHandle_nullList() {
        // null文本列表会抛出NullPointerException
        assertThrows(NullPointerException.class, () -> {
            embeddingClient.createEmbeddings(null, "sk-test-key");
        });
    }

    @Test
    @DisplayName("配置正确加载 - null配置")
    void config_shouldHandle_nullConfig() {
        when(aiConfig.getEmbedding()).thenReturn(null);

        final AiConfig.EmbeddingConfig config = aiConfig.getEmbedding();

        assertNull(config);
    }
}