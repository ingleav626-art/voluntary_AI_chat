package com.voluntary.chat.server.service;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.TargetType;
import com.voluntary.chat.server.dto.response.ConversationResponse;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService 单元测试")
class ConversationServiceTest {

    @Mock
    private MessageService messageService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ConversationService conversationService;

    private Message mockMessage;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1002L);
        mockUser.setUsername("李四");
        mockUser.setAvatar("http://example.com/avatar.jpg");

        mockMessage = new Message();
        mockMessage.setId(2001L);
        mockMessage.setSessionId("p_1001_1002");
        mockMessage.setSenderId(1001L);
        mockMessage.setTargetId(1002L);
        mockMessage.setType(0);
        mockMessage.setContent("你好");
        mockMessage.setCreateTime(LocalDateTime.now());
        mockMessage.setIsDeleted(0);
    }

    @Test
    @DisplayName("获取会话列表成功-有会话")
    void getConversations_shouldSucceed_withConversations() {
        // 模拟获取会话ID列表
        when(messageService.getUserSessionIds(1001L)).thenReturn(List.of("p_1001_1002"));

        // 模拟获取最后一条消息
        when(messageService.getLastMessage("p_1001_1002")).thenReturn(mockMessage);

        // 模拟获取未读数
        when(messageService.getUnreadCount(1001L, "p_1001_1002")).thenReturn(2L);

        // 模拟查询用户
        when(userService.findById(1002L)).thenReturn(mockUser);

        PageResult<ConversationResponse> result = conversationService.getConversations(1001L, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());

        ConversationResponse conv = result.getList().get(0);
        assertEquals("p_1001_1002", conv.getSessionId());
        assertEquals("你好", conv.getLastMessage());
        assertEquals(MessageType.TEXT.name(), conv.getLastMessageType());
        assertEquals(2L, conv.getUnreadCount());
        assertEquals(1002L, conv.getTargetId());
        assertEquals(TargetType.USER.name(), conv.getTargetType());
        assertEquals("李四", conv.getTargetName());
    }

    @Test
    @DisplayName("获取会话列表-无会话")
    void getConversations_shouldReturnEmpty_noConversations() {
        when(messageService.getUserSessionIds(anyLong())).thenReturn(Collections.emptyList());

        PageResult<ConversationResponse> result = conversationService.getConversations(1001L, 1, 20);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("获取会话列表-群聊会话")
    void getConversations_shouldHandle_groupChat() {
        Message groupMessage = new Message();
        groupMessage.setId(3001L);
        groupMessage.setSessionId("g_2001");
        groupMessage.setSenderId(1001L);
        groupMessage.setTargetId(2001L);
        groupMessage.setType(0);
        groupMessage.setContent("大家好");
        groupMessage.setCreateTime(LocalDateTime.now());
        groupMessage.setIsDeleted(0);

        when(messageService.getUserSessionIds(1001L)).thenReturn(List.of("g_2001"));
        when(messageService.getLastMessage("g_2001")).thenReturn(groupMessage);
        when(messageService.getUnreadCount(1001L, "g_2001")).thenReturn(5L);

        PageResult<ConversationResponse> result = conversationService.getConversations(1001L, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());

        ConversationResponse conv = result.getList().get(0);
        assertEquals("g_2001", conv.getSessionId());
        assertEquals(2001L, conv.getTargetId());
        assertEquals(TargetType.GROUP.name(), conv.getTargetType());
        assertEquals("群聊", conv.getTargetName());
    }

    @Test
    @DisplayName("获取会话列表-AI会话")
    void getConversations_shouldHandle_aiChat() {
        Message aiMessage = new Message();
        aiMessage.setId(4001L);
        aiMessage.setSessionId("a_3001_1001");
        aiMessage.setSenderId(1001L);
        aiMessage.setTargetId(3001L);
        aiMessage.setType(0);
        aiMessage.setContent("你好AI");
        aiMessage.setCreateTime(LocalDateTime.now());
        aiMessage.setIsDeleted(0);

        when(messageService.getUserSessionIds(1001L)).thenReturn(List.of("a_3001_1001"));
        when(messageService.getLastMessage("a_3001_1001")).thenReturn(aiMessage);
        when(messageService.getUnreadCount(1001L, "a_3001_1001")).thenReturn(0L);

        PageResult<ConversationResponse> result = conversationService.getConversations(1001L, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());

        ConversationResponse conv = result.getList().get(0);
        assertEquals("a_3001_1001", conv.getSessionId());
        assertEquals(3001L, conv.getTargetId());
        assertEquals(TargetType.USER.name(), conv.getTargetType());
        assertEquals("AI助手", conv.getTargetName());
    }

    @Test
    @DisplayName("获取会话列表-分页")
    void getConversations_shouldPaginate_correctly() {
        Message msg1 = new Message();
        msg1.setSessionId("p_1001_1002");
        msg1.setContent("消息1");
        msg1.setType(0);
        msg1.setCreateTime(LocalDateTime.now().minusMinutes(10));
        msg1.setIsDeleted(0);

        Message msg2 = new Message();
        msg2.setSessionId("p_1001_1003");
        msg2.setContent("消息2");
        msg2.setType(0);
        msg2.setCreateTime(LocalDateTime.now().minusMinutes(5));
        msg2.setIsDeleted(0);

        User user2 = new User();
        user2.setId(1003L);
        user2.setUsername("王五");

        when(messageService.getUserSessionIds(1001L)).thenReturn(List.of("p_1001_1002", "p_1001_1003"));
        when(messageService.getLastMessage("p_1001_1002")).thenReturn(msg1);
        when(messageService.getLastMessage("p_1001_1003")).thenReturn(msg2);
        when(messageService.getUnreadCount(anyLong(), anyString())).thenReturn(0L);
        when(userService.findById(1002L)).thenReturn(mockUser);
        when(userService.findById(1003L)).thenReturn(user2);

        // 第1页，每页1条
        PageResult<ConversationResponse> page1 = conversationService.getConversations(1001L, 1, 1);
        assertEquals(2, page1.getTotal());
        assertEquals(1, page1.getList().size());
        // 按时间倒序，最新的在前面
        assertEquals("p_1001_1003", page1.getList().get(0).getSessionId());

        // 第2页，每页1条
        PageResult<ConversationResponse> page2 = conversationService.getConversations(1001L, 2, 1);
        assertEquals(2, page2.getTotal());
        assertEquals(1, page2.getList().size());
        assertEquals("p_1001_1002", page2.getList().get(0).getSessionId());
    }

    @Test
    @DisplayName("获取会话列表-跳过无最后消息的会话")
    void getConversations_shouldSkip_noLastMessage() {
        when(messageService.getUserSessionIds(1001L)).thenReturn(List.of("p_1001_1002"));
        when(messageService.getLastMessage("p_1001_1002")).thenReturn(null);

        PageResult<ConversationResponse> result = conversationService.getConversations(1001L, 1, 20);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }
}