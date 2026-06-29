package com.voluntary.chat.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageTypes 常量测试")
class MessageTypesTest {

    @Test
    @DisplayName("消息类型常量不为空")
    void allConstantsShouldBeDefined() {
        assertNotNull(MessageTypes.SEND_MESSAGE);
        assertNotNull(MessageTypes.RECEIVE_MESSAGE);
        assertNotNull(MessageTypes.GROUP_MESSAGE);
        assertNotNull(MessageTypes.AI_CHAT);
        assertNotNull(MessageTypes.AI_STREAM);
        assertNotNull(MessageTypes.MESSAGE_ACK);
        assertNotNull(MessageTypes.STATUS_CHANGE);
        assertNotNull(MessageTypes.READ_RECEIPT);
        assertNotNull(MessageTypes.PING);
        assertNotNull(MessageTypes.PONG);
        assertNotNull(MessageTypes.RECONNECT);
        assertNotNull(MessageTypes.RECONNECT_ACK);
        assertNotNull(MessageTypes.FORCE_LOGOUT);

        // 群组事件
        assertNotNull(MessageTypes.GROUP_MEMBER_JOIN);
        assertNotNull(MessageTypes.GROUP_MEMBER_LEAVE);
        assertNotNull(MessageTypes.GROUP_MEMBER_ROLE_CHANGE);
        assertNotNull(MessageTypes.GROUP_INFO_CHANGE);
        assertNotNull(MessageTypes.GROUP_DISMISSED);

        // 通知消息
        assertNotNull(MessageTypes.NOTIFICATION);
        assertNotNull(MessageTypes.NOTIFICATION_NEW_MESSAGE);
        assertNotNull(MessageTypes.NOTIFICATION_AI_GREETING);
        assertNotNull(MessageTypes.NOTIFICATION_TODO_REMINDER);
        assertNotNull(MessageTypes.NOTIFICATION_SYSTEM_EVENT);
        assertNotNull(MessageTypes.NOTIFICATION_SETTINGS_CHANGED);
    }

    @Test
    @DisplayName("SEND_MESSAGE 常量的值")
    void sendMessageValue() {
        assertEquals("SEND_MESSAGE", MessageTypes.SEND_MESSAGE);
    }

    @Test
    @DisplayName("PING 和 PONG 成对")
    void pingPong() {
        assertEquals("PING", MessageTypes.PING);
        assertEquals("PONG", MessageTypes.PONG);
    }

    @Test
    @DisplayName("所有常量值唯一")
    void allValuesShouldBeUnique() {
        java.lang.reflect.Field[] fields = MessageTypes.class.getFields();
        long distinctCount = java.util.Arrays.stream(fields)
                .map(f -> {
                    try { return (String) f.get(null); } catch (Exception e) { return ""; }
                })
                .distinct()
                .count();
        assertEquals(fields.length, distinctCount);
    }
}
