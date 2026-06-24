package com.voluntary.chat.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import com.voluntary.chat.server.service.AiChatService;
import com.voluntary.chat.server.service.AiGroupConfigService;
import com.voluntary.chat.server.service.MessageService;
import com.voluntary.chat.server.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatWebSocketHandler 测试")
class ChatWebSocketHandlerTest {

    private ChatWebSocketHandler handler;

    @Mock
    private MessageService messageService;

    @Mock
    private UserService userService;

    @Mock
    private GroupMemberMapper groupMemberMapper;

    @Mock
    private AiChatService aiChatService;

    private ObjectMapper objectMapper;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @Mock
    private AiGroupConfigService aiGroupConfigService;

    private static final Long USER_ID_1 = 1001L;
    private static final Long USER_ID_2 = 1002L;
    private static final Long GROUP_ID = 2001L;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        handler = new ChatWebSocketHandler(messageService, userService, groupMemberMapper, aiChatService, aiGroupConfigService, objectMapper);

        // 重置静态 ONLINE_SESSIONS，避免测试间相互影响
        Field field = ChatWebSocketHandler.class.getDeclaredField("ONLINE_SESSIONS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, WebSocketSession> map = (ConcurrentHashMap<Long, WebSocketSession>) field.get(null);
        map.clear();
    }

    /** 创建模拟 session 并建立连接 */
    private void createSession(Long userId, WebSocketSession mockSession) throws Exception {
        when(mockSession.getAttributes()).thenReturn(Map.of(JwtHandshakeInterceptor.USER_ID_KEY, userId));
        when(mockSession.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(mockSession);
    }

    @Test
    @DisplayName("连接建立 - 成功带 userId")
    void afterConnectionEstablished_shouldSucceed() throws Exception {
        createSession(USER_ID_1, session1);

        assertTrue(handler.isUserOnline(USER_ID_1));
        verify(session1, never()).close(any());
    }

    @Test
    @DisplayName("连接建立 - 无 userId 则拒绝")
    void afterConnectionEstablished_shouldReject_withoutUserId() throws Exception {
        when(session1.getAttributes()).thenReturn(Map.of());

        handler.afterConnectionEstablished(session1);

        assertFalse(handler.isUserOnline(USER_ID_1));
        verify(session1).close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Test
    @DisplayName("连接建立 - 顶号：旧连接被踢下线")
    void afterConnectionEstablished_shouldKick_oldSession() throws Exception {
        createSession(USER_ID_1, session1);
        createSession(USER_ID_1, session2);

        // session1 应该收到 FORCE_LOGOUT 消息并被关闭
        verify(session1).sendMessage(any(TextMessage.class));
        verify(session1).close(CloseStatus.NORMAL);
        // 在线状态应保持不变（session2 活跃）
        assertTrue(handler.isUserOnline(USER_ID_1));
    }

    @Test
    @DisplayName("连接关闭 - 只移除当前活跃 session，不误删新 session")
    void afterConnectionClosed_shouldNotRemove_newSession() throws Exception {
        createSession(USER_ID_1, session1);
        createSession(USER_ID_1, session2);

        // session1 关闭（旧连接关闭的回调）
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        // 用户应仍在线（session2 还在）
        assertTrue(handler.isUserOnline(USER_ID_1));
    }

    @Test
    @DisplayName("连接关闭 - 最后一个连接断开时广播离线")
    void afterConnectionClosed_shouldBroadcastOffline_whenLastSession() throws Exception {
        createSession(USER_ID_1, session1);

        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        assertFalse(handler.isUserOnline(USER_ID_1));
    }

    @Test
    @DisplayName("连接关闭 - 无 userId 的 session 忽略")
    void afterConnectionClosed_shouldIgnore_withoutUserId() {
        when(session1.getAttributes()).thenReturn(Map.of());

        assertDoesNotThrow(() -> handler.afterConnectionClosed(session1, CloseStatus.NORMAL));
    }

    @Test
    @DisplayName("处理私聊消息 SEND_MESSAGE - 成功转发")
    void handleTextMessage_shouldForwardPrivateMessage() throws Exception {
        createSession(USER_ID_1, session1);

        User sender = new User();
        sender.setId(USER_ID_1);
        sender.setUsername("张三");

        SendMessageResponse sendResponse = SendMessageResponse.builder()
                .messageId(10001L)
                .createTime(LocalDateTime.now())
                .build();

        String payload = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "type": "SEND_MESSAGE",
                    "data": {
                        "sessionId": "p_1001_1002",
                        "msgType": "TEXT",
                        "content": "你好"
                    }
                }
                """;

        when(messageService.sendMessage(eq(USER_ID_1), any(SendMessageRequest.class)))
                .thenReturn(sendResponse);
        when(userService.findById(USER_ID_1)).thenReturn(sender);

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        verify(messageService).sendMessage(eq(USER_ID_1), any(SendMessageRequest.class));
        // MESSAGE_ACK 应发送给 session1
        verify(session1, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("处理群聊消息 SEND_MESSAGE - 广播给群成员")
    void handleTextMessage_shouldBroadcastGroupMessage() throws Exception {
        createSession(USER_ID_1, session1);

        User sender = new User();
        sender.setId(USER_ID_1);
        sender.setUsername("张三");

        SendMessageResponse sendResponse = SendMessageResponse.builder()
                .messageId(10002L)
                .createTime(LocalDateTime.now())
                .build();

        String payload = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440001",
                    "type": "SEND_MESSAGE",
                    "data": {
                        "sessionId": "g_2001",
                        "msgType": "TEXT",
                        "content": "大家好"
                    }
                }
                """;

        when(messageService.sendMessage(eq(USER_ID_1), any(SendMessageRequest.class)))
                .thenReturn(sendResponse);
        when(userService.findById(USER_ID_1)).thenReturn(sender);
        when(groupMemberMapper.selectGroupMemberUserIds(GROUP_ID))
                .thenReturn(List.of(USER_ID_1, USER_ID_2));

        // 模拟接收者在另一会话在线
        when(session2.isOpen()).thenReturn(true);
        when(session2.getAttributes()).thenReturn(Map.of(JwtHandshakeInterceptor.USER_ID_KEY, USER_ID_2));
        handler.afterConnectionEstablished(session2);

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        // 群消息应发送给 USER_ID_2（排除发送者 USER_ID_1）
        verify(groupMemberMapper).selectGroupMemberUserIds(GROUP_ID);
    }

    @Test
    @DisplayName("处理心跳 PING - 返回 PONG")
    void handleTextMessage_shouldRespondPong() throws Exception {
        createSession(USER_ID_1, session1);

        String payload = """
                {"id":"ping-001","type":"PING","data":{}}
                """;

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        // 应返回 PONG
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        String sentPayload = captor.getValue().getPayload();
        assertTrue(sentPayload.contains(MessageTypes.PONG));
    }

    @Test
    @DisplayName("处理未知消息类型 - 不抛异常")
    void handleTextMessage_shouldIgnore_unknownType() throws Exception {
        createSession(USER_ID_1, session1);

        String payload = """
                {"id":"unknown","type":"UNKNOWN","data":{}}
                """;

        TextMessage textMessage = new TextMessage(payload);
        assertDoesNotThrow(() -> handler.handleTextMessage(session1, textMessage));
    }

    @Test
    @DisplayName("处理无效 JSON 消息 - 不抛异常")
    void handleTextMessage_shouldHandle_invalidJson() throws Exception {
        createSession(USER_ID_1, session1);

        TextMessage textMessage = new TextMessage("not-json-at-all");
        assertDoesNotThrow(() -> handler.handleTextMessage(session1, textMessage));
    }

    @Test
    @DisplayName("传输错误 - 不抛异常")
    void handleTransportError_shouldNotThrow() {
        when(session1.getAttributes()).thenReturn(Map.of(JwtHandshakeInterceptor.USER_ID_KEY, USER_ID_1));
        assertDoesNotThrow(() -> handler.handleTransportError(session1, new IOException("network error")));
    }

    @Test
    @DisplayName("sendToUser - 在线用户成功推送")
    void sendToUser_shouldSend_whenOnline() throws Exception {
        createSession(USER_ID_1, session1);

        WebSocketMessage msg = WebSocketMessage.builder()
                .id("msg-1")
                .type(MessageTypes.RECEIVE_MESSAGE)
                .data(Map.of())
                .build();

        handler.sendToUser(USER_ID_1, msg);

        verify(session1).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("sendToUser - 离线用户不做推送")
    void sendToUser_shouldNotSend_whenOffline() throws Exception {
        WebSocketMessage msg = WebSocketMessage.builder()
                .id("msg-1")
                .type(MessageTypes.RECEIVE_MESSAGE)
                .data(Map.of())
                .build();

        handler.sendToUser(USER_ID_1, msg);

        verify(session1, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("isUserOnline - 正确返回在线状态")
    void isUserOnline_shouldReturnCorrectStatus() throws Exception {
        assertFalse(handler.isUserOnline(USER_ID_1));
        assertFalse(handler.isUserOnline(USER_ID_2));

        createSession(USER_ID_1, session1);

        assertTrue(handler.isUserOnline(USER_ID_1));
        assertFalse(handler.isUserOnline(USER_ID_2));

        // 断开后应返回离线
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);
        assertFalse(handler.isUserOnline(USER_ID_1));
    }
}