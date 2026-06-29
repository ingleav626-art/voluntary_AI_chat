package com.voluntary.chat.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiConfig 配置类测试
 */
@DisplayName("AiConfig 测试")
class AiConfigTest {

    private AiConfig aiConfig;

    @BeforeEach
    void setUp() {
        aiConfig = new AiConfig();
    }

    @Test
    @DisplayName("默认值 - 温度和Token数")
    void defaultValues_shouldBeCorrect() {
        assertEquals(0.7, aiConfig.getDefaultTemperature());
        assertEquals(2048, aiConfig.getDefaultMaxTokens());
    }

    @Test
    @DisplayName("ContextConfig 默认值")
    void contextConfig_shouldHaveDefaults() {
        final AiConfig.ContextConfig context = aiConfig.getContext();
        assertNotNull(context);
        assertEquals(10, context.getMaxHistoryRounds());
        assertEquals(3, context.getMaxMemoryCount());
        assertEquals(0.7, context.getMemorySimilarityThreshold());
    }

    @Test
    @DisplayName("设置和获取 encryptionKey")
    void encryptionKey_shouldBeSettable() {
        aiConfig.setEncryptionKey("test-key-32-bytes-long-enough!!");
        assertEquals("test-key-32-bytes-long-enough!!", aiConfig.getEncryptionKey());
    }
}