package com.voluntary.chat.server.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiProfile 实体测试
 */
@DisplayName("AiProfile 实体测试")
class AiProfileTest {

    private AiProfile aiProfile;

    @BeforeEach
    void setUp() {
        aiProfile = new AiProfile();
    }

    @Test
    @DisplayName("设置和获取字段")
    void setAndGet_shouldWork() {
        aiProfile.setId(1L);
        aiProfile.setUserId(1001L);
        aiProfile.setName("测试AI");
        aiProfile.setModel("gpt-4");
        aiProfile.setApiKeyEnc("encrypted-key");
        aiProfile.setStatus(0);
        aiProfile.setIsGroup(false);
        aiProfile.setTemperature(0.7);
        aiProfile.setMaxTokens(2048);

        assertEquals(1L, aiProfile.getId());
        assertEquals(1001L, aiProfile.getUserId());
        assertEquals("测试AI", aiProfile.getName());
        assertEquals("gpt-4", aiProfile.getModel());
        assertEquals("encrypted-key", aiProfile.getApiKeyEnc());
        assertEquals(0, aiProfile.getStatus());
        assertFalse(aiProfile.getIsGroup());
        assertEquals(0.7, aiProfile.getTemperature());
        assertEquals(2048, aiProfile.getMaxTokens());
    }
}