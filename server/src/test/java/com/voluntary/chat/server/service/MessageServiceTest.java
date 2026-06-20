package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.MarkReadRequest;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.MessageResponse;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.MessageRead;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.mapper.MessageReadMapper;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService 单元测试")
class MessageServiceTest {

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MessageReadMapper messageReadMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private MessageService messageService;

    private Message mockMessage;
    private User mockSender;

    @BeforeEach
    void setUp() {
        mockSender = new User();
        mockSender.setId(1001L);
        mockSender.setUsername("张三");
        mockSender.setAvatar("http://example.com/avatar.jpg");

        mockMessage = new Message();
        mockMessage.setId(2001L);
        mockMessage.setSessionId("p_1001_1002");
        mockMessage.setSenderId(1001L);
        mockMessage.setSenderType(0);
        mockMessage.setTargetId(1002L);
        mockMessage.setTargetType(0);
        mockMessage.setType(0);
        mockMessage.setContent("你好");
        mockMessage.setCreateTime(LocalDateTime.now());
        mockMessage.setIsDeleted(0);
    }

    @Test
    @DisplayName("发送消息成功-单聊")
    void sendMessage_shouldSucceed_privateChat() {
        SendMessageRequest request = new SendMessageRequest();
        request.setSessionId("p_1001_1002");
        request.setType("TEXT");
        request.setContent("你好");

        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        SendMessageResponse response = messageService.sendMessage(1001L, request);

        assertNotNull(response);
        verify(messageMapper).insert(any(Message.class));
    }

    @Test
    @DisplayName("发送消息失败-不支持的消息类型")
    void sendMessage_shouldFail_unsupportedType() {
        SendMessageRequest request = new SendMessageRequest();
        request.setSessionId("p_1001_1002");
        request.setType("INVALID");
        request.setContent("你好");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageService.sendMessage(1001L, request));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("发送消息失败-无效的sessionId格式")
    void sendMessage_shouldFail_invalidSessionId() {
        SendMessageRequest request = new SendMessageRequest();
        request.setSessionId("invalid_session");
        request.setType("TEXT");
        request.setContent("你好");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageService.sendMessage(1001L, request));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("获取聊天记录成功")
    void getHistory_shouldSucceed() {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mockMessage));
        when(userService.findByIds(any(Set.class))).thenReturn(Map.of(1001L, mockSender));

        PageResult<MessageResponse> result = messageService.getHistory("p_1001_1002", 1002L, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("你好", result.getList().get(0).getContent());
    }

    @Test
    @DisplayName("获取聊天记录-无消息")
    void getHistory_shouldReturnEmpty() {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(userService.findByIds(any(Set.class))).thenReturn(Collections.emptyMap());

        PageResult<MessageResponse> result = messageService.getHistory("p_1001_1002", 1002L, 1, 20);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("撤回消息成功-2分钟内")
    void recallMessage_shouldSucceed_withinTimeout() {
        mockMessage.setCreateTime(LocalDateTime.now().minusMinutes(1));

        when(messageMapper.selectById(2001L)).thenReturn(mockMessage);
        when(messageMapper.updateById(any(Message.class))).thenReturn(1);

        assertDoesNotThrow(() -> messageService.recallMessage(1001L, 2001L));
        verify(messageMapper).updateById(any(Message.class));
    }

    @Test
    @DisplayName("撤回消息失败-超过2分钟")
    void recallMessage_shouldFail_afterTimeout() {
        mockMessage.setCreateTime(LocalDateTime.now().minusMinutes(3));

        when(messageMapper.selectById(2001L)).thenReturn(mockMessage);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageService.recallMessage(1001L, 2001L));
        assertEquals(ErrorCode.MESSAGE_RECALL_TIMEOUT, exception.getErrorCode());
    }

    @Test
    @DisplayName("撤回消息失败-非本人消息")
    void recallMessage_shouldFail_notOwner() {
        mockMessage.setCreateTime(LocalDateTime.now().minusMinutes(1));

        when(messageMapper.selectById(2001L)).thenReturn(mockMessage);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageService.recallMessage(9999L, 2001L));
        assertEquals(ErrorCode.NO_PERMISSION_TO_RECALL, exception.getErrorCode());
    }

    @Test
    @DisplayName("撤回消息失败-消息不存在")
    void recallMessage_shouldFail_messageNotFound() {
        when(messageMapper.selectById(9999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageService.recallMessage(1001L, 9999L));
        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("标记消息已读成功")
    void markRead_shouldSucceed() {
        MarkReadRequest request = new MarkReadRequest();
        request.setSessionId("p_1001_1002");
        request.setMessageIds(List.of("2001", "2002"));

        when(messageReadMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(messageReadMapper.insert(any(MessageRead.class))).thenReturn(1);

        assertDoesNotThrow(() -> messageService.markRead(1002L, request));
        verify(messageReadMapper, times(2)).insert(any(MessageRead.class));
    }

    @Test
    @DisplayName("标记已读-跳过已读消息")
    void markRead_shouldSkip_alreadyRead() {
        MarkReadRequest request = new MarkReadRequest();
        request.setSessionId("p_1001_1002");
        request.setMessageIds(List.of("2001"));

        when(messageReadMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertDoesNotThrow(() -> messageService.markRead(1002L, request));
        verify(messageReadMapper, never()).insert(any(MessageRead.class));
    }

    @Test
    @DisplayName("获取未读消息数")
    void getUnreadCount_shouldReturnCorrectCount() {
        Message msg1 = new Message();
        msg1.setId(2001L);
        msg1.setSenderId(1001L);
        msg1.setRecallTime(null);
        msg1.setIsDeleted(0);

        Message msg2 = new Message();
        msg2.setId(2002L);
        msg2.setSenderId(1001L);
        msg2.setRecallTime(null);
        msg2.setIsDeleted(0);

        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(msg1, msg2));
        when(messageReadMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        long count = messageService.getUnreadCount(1002L, "p_1001_1002");

        assertEquals(1, count);
    }

    @Test
    @DisplayName("获取最后一条消息")
    void getLastMessage_shouldReturnLatest() {
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockMessage);

        Message result = messageService.getLastMessage("p_1001_1002");

        assertNotNull(result);
        assertEquals("你好", result.getContent());
    }
}
