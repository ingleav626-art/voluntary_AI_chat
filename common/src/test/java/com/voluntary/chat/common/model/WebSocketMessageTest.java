package com.voluntary.chat.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage 测试")
class WebSocketMessageTest {

    @Test
    @DisplayName("Builder 构造")
    void builder() {
        WebSocketMessage msg = WebSocketMessage.builder()
                .id("msg-1")
                .type("SEND_MESSAGE")
                .data("hello")
                .build();

        assertEquals("msg-1", msg.getId());
        assertEquals("SEND_MESSAGE", msg.getType());
        assertEquals("hello", msg.getData());
    }

    @Test
    @DisplayName("无参构造")
    void noArgsConstructor() {
        WebSocketMessage msg = new WebSocketMessage();
        assertNull(msg.getId());
        assertNull(msg.getType());
        assertNull(msg.getData());
    }

    @Test
    @DisplayName("全参构造")
    void allArgsConstructor() {
        WebSocketMessage msg = new WebSocketMessage("msg-2", "PING", null);
        assertEquals("msg-2", msg.getId());
        assertEquals("PING", msg.getType());
        assertNull(msg.getData());
    }

    @Test
    @DisplayName("Setter 和 Getter")
    void setterAndGetter() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setId("msg-3");
        msg.setType("PONG");
        msg.setData(new Object());

        assertEquals("msg-3", msg.getId());
        assertEquals("PONG", msg.getType());
        assertNotNull(msg.getData());
    }
}
