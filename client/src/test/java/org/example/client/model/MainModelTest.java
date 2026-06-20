package org.example.client.model;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主界面数据模型测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class MainModelTest {

    @Test
    void conversationInfo_shouldSetAndGetAllFields() {
        final LocalDateTime now = LocalDateTime.now();
        final ConversationInfo conv = new ConversationInfo();
        conv.setSessionId("p_1001_1002");
        conv.setTargetId(1002L);
        conv.setTargetType("USER");
        conv.setTargetName("李四");
        conv.setTargetAvatar("http://example.com/avatar.jpg");
        conv.setLastMessage("你好");
        conv.setLastMessageType("TEXT");
        conv.setLastMessageTime(now);
        conv.setUnreadCount(3);

        assertEquals("p_1001_1002", conv.getSessionId());
        assertEquals(1002L, conv.getTargetId());
        assertEquals("USER", conv.getTargetType());
        assertEquals("李四", conv.getTargetName());
        assertEquals("http://example.com/avatar.jpg", conv.getTargetAvatar());
        assertEquals("你好", conv.getLastMessage());
        assertEquals("TEXT", conv.getLastMessageType());
        assertEquals(now, conv.getLastMessageTime());
        assertEquals(3, conv.getUnreadCount());
    }

    @Test
    void conversationInfo_shouldSupportAllArgsConstructor() {
        final LocalDateTime now = LocalDateTime.now();
        final ConversationInfo conv = new ConversationInfo(
                "g_2001", 2001L, "GROUP", "技术群",
                "http://example.com/group.jpg", "大家好",
                "TEXT", now, 10);

        assertEquals("g_2001", conv.getSessionId());
        assertEquals(2001L, conv.getTargetId());
        assertEquals("GROUP", conv.getTargetType());
        assertEquals("技术群", conv.getTargetName());
        assertEquals(10, conv.getUnreadCount());
    }

    @Test
    void messageInfo_shouldSetAndGetAllFields() {
        final LocalDateTime now = LocalDateTime.now();
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(1L);
        msg.setSessionId("p_1001_1002");
        msg.setSenderId(1001L);
        msg.setSenderName("张三");
        msg.setSenderAvatar("http://example.com/avatar.jpg");
        msg.setSenderType("USER");
        msg.setType("TEXT");
        msg.setContent("测试消息");
        msg.setCreateTime(now);
        msg.setRecalled(false);
        msg.setSentByMe(true);

        assertEquals(1L, msg.getMessageId());
        assertEquals("p_1001_1002", msg.getSessionId());
        assertEquals(1001L, msg.getSenderId());
        assertEquals("张三", msg.getSenderName());
        assertEquals("http://example.com/avatar.jpg", msg.getSenderAvatar());
        assertEquals("USER", msg.getSenderType());
        assertEquals("TEXT", msg.getType());
        assertEquals("测试消息", msg.getContent());
        assertEquals(now, msg.getCreateTime());
        assertFalse(msg.isRecalled());
        assertTrue(msg.isSentByMe());
    }

    @Test
    void pageResult_shouldSetAndGetFields() {
        final PageResult<String> result = new PageResult<>();
        result.setList(java.util.Arrays.asList("a", "b", "c"));
        result.setTotal(100);
        result.setPage(1);
        result.setSize(20);

        assertNotNull(result.getList());
        assertEquals(3, result.getList().size());
        assertEquals(100, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getSize());
    }

    @Test
    void sendMessageRequest_shouldSetAndGetFields() {
        final SendMessageRequest request = new SendMessageRequest();
        request.setSessionId("p_1001_1002");
        request.setType("TEXT");
        request.setContent("你好");

        assertEquals("p_1001_1002", request.getSessionId());
        assertEquals("TEXT", request.getType());
        assertEquals("你好", request.getContent());
    }

    @Test
    void markReadRequest_shouldSetAndGetFields() {
        final MarkReadRequest request = new MarkReadRequest();
        request.setSessionId("p_1001_1002");
        request.setMessageIds(java.util.Arrays.asList(1L, 2L, 3L));

        assertEquals("p_1001_1002", request.getSessionId());
        assertNotNull(request.getMessageIds());
        assertEquals(3, request.getMessageIds().size());
    }
}
