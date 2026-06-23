package com.voluntary.chat.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageType 枚举测试")
class MessageTypeTest {

    @Test
    @DisplayName("TEXT 枚举值")
    void text() {
        assertEquals("文本", MessageType.TEXT.getDescription());
    }

    @Test
    @DisplayName("IMAGE 枚举值")
    void image() {
        assertEquals("图片", MessageType.IMAGE.getDescription());
    }

    @Test
    @DisplayName("AI 枚举值")
    void ai() {
        assertEquals("AI", MessageType.AI.getDescription());
    }

    @Test
    @DisplayName("RECALL 枚举值")
    void recall() {
        assertEquals("撤回", MessageType.RECALL.getDescription());
    }

    @Test
    @DisplayName("FORWARD 枚举值")
    void forward() {
        assertEquals("转发", MessageType.FORWARD.getDescription());
    }

    @Test
    @DisplayName("枚举数量")
    void enumCount() {
        assertEquals(6, MessageType.values().length);
    }

    @Test
    @DisplayName("valueOf 查找")
    void valueOf() {
        assertEquals(MessageType.TEXT, MessageType.valueOf("TEXT"));
        assertEquals(MessageType.IMAGE, MessageType.valueOf("IMAGE"));
    }
}
