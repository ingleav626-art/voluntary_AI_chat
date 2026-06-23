package com.voluntary.chat.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SenderType 枚举测试")
class SenderTypeTest {

    @Test
    @DisplayName("USER 枚举值")
    void user() {
        assertEquals("用户", SenderType.USER.getDescription());
    }

    @Test
    @DisplayName("AI 枚举值")
    void ai() {
        assertEquals("AI", SenderType.AI.getDescription());
    }

    @Test
    @DisplayName("枚举数量")
    void enumCount() {
        assertEquals(3, SenderType.values().length);
    }

    @Test
    @DisplayName("valueOf 查找")
    void valueOf() {
        assertEquals(SenderType.USER, SenderType.valueOf("USER"));
        assertEquals(SenderType.AI, SenderType.valueOf("AI"));
    }
}
