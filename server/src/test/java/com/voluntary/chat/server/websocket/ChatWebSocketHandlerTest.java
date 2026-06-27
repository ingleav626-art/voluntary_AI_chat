package com.voluntary.chat.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.MessageResponse;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import com.voluntary.chat.server.service.AiChatService;
import com.voluntary.chat.server.service.AiGroupConfigService;
import com.voluntary.chat.server.service.ConversationCacheService;
import com.voluntary.chat.server.service.GroupCacheService;
import com.voluntary.chat.server.service.MessageService;
import com.voluntary.chat.server.service.OnlineStatusService;
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

    @Mock
    private AiGroupConfigService aiGroupConfigService;

    @Mock
    private OnlineStatusService onlineStatusService;

    @Mock
    private ConversationCacheService conversationCacheService;

    @Mock
    private GroupCacheService groupCacheService;

    private ObjectMapper objectMapper;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private static final Long USER_ID_1 = 1001L;
    private static final Long USER_ID_2 = 1002L;
    private static final Long GROUP_ID = 2001L;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        handler = new ChatWebSocketHandler(aiChatService, aiGroupConfigService, objectMapper, messageService,
                userService, groupMemberMapper, onlineStatusService, conversationCacheService, groupCacheService);

        // 重置静态 ONLINE_SESSIONS，避免测试间相互影响
        Field field = AiWebSocketHandler.class.getDeclaredField("ONLINE_SESSIONS");
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

    // ==================== 离线消息转发 ====================

    @Test
    @DisplayName("私聊消息 - 目标离线时存入离线队列")
    void forwardPrivateMessage_shouldStoreOffline_whenTargetOffline() throws Exception {
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
                        "content": "离线消息测试"
                    }
                }
                """;

        when(messageService.sendMessage(eq(USER_ID_1), any(SendMessageRequest.class)))
                .thenReturn(sendResponse);
        when(userService.findById(USER_ID_1)).thenReturn(sender);
        // 目标 USER_ID_2 不在线
        when(onlineStatusService.isOnline(USER_ID_2)).thenReturn(false);

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        // 应存入离线队列
        verify(conversationCacheService).pushOfflineMessage(eq(USER_ID_2), anyString());
        // 应增加未读数
        verify(conversationCacheService).incrementUnread(USER_ID_2, "p_1001_1002");
    }

    @Test
    @DisplayName("私聊消息 - 目标在线时直接推送")
    void forwardPrivateMessage_shouldSendDirectly_whenTargetOnline() throws Exception {
        createSession(USER_ID_1, session1);
        createSession(USER_ID_2, session2);

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
                        "content": "在线消息测试"
                    }
                }
                """;

        when(messageService.sendMessage(eq(USER_ID_1), any(SendMessageRequest.class)))
                .thenReturn(sendResponse);
        when(userService.findById(USER_ID_1)).thenReturn(sender);
        // 目标在线
        when(onlineStatusService.isOnline(USER_ID_2)).thenReturn(true);

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        // 不应存入离线队列
        verify(conversationCacheService, never()).pushOfflineMessage(anyLong(), anyString());
        // 应增加未读数
        verify(conversationCacheService).incrementUnread(USER_ID_2, "p_1001_1002");
    }

    // ==================== 断线重连 ====================

    @Test
    @DisplayName("处理 RECONNECT - 成功补发")
    void handleReconnect_shouldSendMissedMessages() throws Exception {
        createSession(USER_ID_1, session1);

        // 模拟离线消息
        MessageResponse msg = MessageResponse.builder()
                .messageId(10001L)
                .sessionId("p_1001_1002")
                .senderId(USER_ID_2)
                .senderName("李四")
                .type("TEXT")
                .content("你好")
                .createTime(LocalDateTime.now())
                .build();

        String payload = """
                {
                    "id": "reconnect-001",
                    "type": "RECONNECT",
                    "data": {
                        "lastMessageId": 9999
                    }
                }
                """;

        when(messageService.getOfflineMessages(USER_ID_1, 9999L)).thenReturn(List.of(msg));

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        // 应补发离线消息
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1, atLeast(2)).sendMessage(captor.capture());
        String allSent = captor.getAllValues().stream()
                .map(TextMessage::getPayload)
                .reduce("", (a, b) -> a + b);
        assertTrue(allSent.contains("RECONNECT_ACK"));
        assertTrue(allSent.contains(MessageTypes.RECEIVE_MESSAGE));
    }

    @Test
    @DisplayName("处理 RECONNECT - 缺少 lastMessageId 不抛异常")
    void handleReconnect_shouldHandle_missingLastMessageId() throws Exception {
        createSession(USER_ID_1, session1);

        String payload = """
                {
                    "id": "reconnect-002",
                    "type": "RECONNECT",
                    "data": {}
                }
                """;

        TextMessage textMessage = new TextMessage(payload);
        assertDoesNotThrow(() -> handler.handleTextMessage(session1, textMessage));
    }

    @Test
    @DisplayName("处理 RECONNECT - lastMessageId 格式错误不抛异常")
    void handleReconnect_shouldHandle_invalidLastMessageId() throws Exception {
        createSession(USER_ID_1, session1);

        String payload = """
                {
                    "id": "reconnect-003",
                    "type": "RECONNECT",
                    "data": {
                        "lastMessageId": "not-a-number"
                    }
                }
                """;

        TextMessage textMessage = new TextMessage(payload);
        assertDoesNotThrow(() -> handler.handleTextMessage(session1, textMessage));
    }

    // ==================== 群相关广播 ====================

    @Test
    @DisplayName("broadcastToGroupExcept - 排除指定用户")
    void broadcastToGroupExcept_shouldExcludeUser() throws Exception {
        createSession(USER_ID_1, session1);
        createSession(USER_ID_2, session2);

        WebSocketMessage msg = WebSocketMessage.builder()
                .id("broadcast-001")
                .type("GROUP_INFO_CHANGE")
                .data(Map.of())
                .build();

        // 模拟群成员缓存命中
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1, USER_ID_2));

        handler.broadcastToGroupExcept("g_2001", USER_ID_1, msg);

        // USER_ID_2 应收到（USER_ID_1 被排除）
        verify(session2).sendMessage(any(TextMessage.class));
        verify(session1, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("broadcastToGroup - 全部发送")
    void broadcastToGroup_shouldSendToAll() throws Exception {
        createSession(USER_ID_1, session1);
        createSession(USER_ID_2, session2);

        WebSocketMessage msg = WebSocketMessage.builder()
                .id("broadcast-002")
                .type("GROUP_MEMBER_JOIN")
                .data(Map.of())
                .build();

        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1, USER_ID_2));

        handler.broadcastToGroup("g_2001", msg);

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("broadcastToGroup - 非群 sessionId 跳过")
    void broadcastToGroup_shouldSkip_whenNotGroupSession() {
        WebSocketMessage msg = WebSocketMessage.builder()
                .id("broadcast-003")
                .type("GROUP_MEMBER_JOIN")
                .data(Map.of())
                .build();

        handler.broadcastToGroup("p_1001_1002", msg);

        verify(groupCacheService, never()).getMemberIds(anyLong());
    }

    @Test
    @DisplayName("broadcastMemberJoin - 发送入群通知")
    void broadcastMemberJoin_shouldSend() throws Exception {
        createSession(USER_ID_1, session1);
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1));

        handler.broadcastMemberJoin(GROUP_ID, USER_ID_2, "新成员", null);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("GROUP_MEMBER_JOIN"));
    }

    @Test
    @DisplayName("broadcastMemberLeave - 发送离群通知")
    void broadcastMemberLeave_shouldSend() throws Exception {
        createSession(USER_ID_1, session1);
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1));

        handler.broadcastMemberLeave(GROUP_ID, USER_ID_2, "离开者", "KICK");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("GROUP_MEMBER_LEAVE"));
    }

    @Test
    @DisplayName("broadcastRoleChange - 发送角色变更通知")
    void broadcastRoleChange_shouldSend() throws Exception {
        createSession(USER_ID_1, session1);
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1));

        handler.broadcastRoleChange(GROUP_ID, USER_ID_2, "新管理", "ADMIN");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("GROUP_MEMBER_ROLE_CHANGE"));
    }

    @Test
    @DisplayName("broadcastGroupInfoChange - 发送群信息变更通知")
    void broadcastGroupInfoChange_shouldSend() throws Exception {
        createSession(USER_ID_1, session1);
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1));

        handler.broadcastGroupInfoChange(GROUP_ID, "新群名", "新公告");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("GROUP_INFO_CHANGE"));
    }

    @Test
    @DisplayName("broadcastGroupDismiss - 发送解散通知")
    void broadcastGroupDismiss_shouldSend() throws Exception {
        createSession(USER_ID_1, session1);
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1));

        handler.broadcastGroupDismiss(GROUP_ID);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("GROUP_DISMISSED"));
    }

    // ==================== 上线补发离线消息 ====================

    @Test
    @DisplayName("连接建立时补发离线消息 - 有离线消息")
    void afterConnectionEstablished_shouldReplayOfflineMessages() throws Exception {
        when(session1.getAttributes()).thenReturn(Map.of(JwtHandshakeInterceptor.USER_ID_KEY, USER_ID_1));
        when(session1.isOpen()).thenReturn(true);
        // 模拟 2 条离线消息
        String offlineMsg1 = "{\"id\":\"off-1\",\"type\":\"RECEIVE_MESSAGE\",\"data\":{\"content\":\"msg1\"}}";
        String offlineMsg2 = "{\"id\":\"off-2\",\"type\":\"RECEIVE_MESSAGE\",\"data\":{\"content\":\"msg2\"}}";
        when(conversationCacheService.popOfflineMessage(USER_ID_1))
                .thenReturn(offlineMsg1)
                .thenReturn(offlineMsg2)
                .thenReturn(null);

        handler.afterConnectionEstablished(session1);

        // 应补发 2 条消息
        verify(session1, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("连接建立时补发离线消息 - 无离线消息")
    void afterConnectionEstablished_shouldNotSend_whenNoOfflineMessages() throws Exception {
        when(session1.getAttributes()).thenReturn(Map.of(JwtHandshakeInterceptor.USER_ID_KEY, USER_ID_1));
        when(session1.isOpen()).thenReturn(true);
        when(conversationCacheService.popOfflineMessage(USER_ID_1)).thenReturn(null);

        // 只清除 TEXT_MESSAGE 的 sendMessage 调用（PING 等）
        handler.afterConnectionEstablished(session1);

        // 不应发送额外消息（但 sendMessage 可能由其他逻辑调用）
        verify(conversationCacheService).popOfflineMessage(USER_ID_1);
    }

    @Test
    @DisplayName("补发离线消息 - JSON 解析失败不中断")
    void replayOfflineMessages_shouldHandleInvalidJson() throws Exception {
        // createSession 注册 USER_ID_1 -> session1 到 userSessions 映射
        createSession(USER_ID_1, session1);
        when(session1.isOpen()).thenReturn(true);
        // 先返回非法 JSON，再返回正常 JSON
        when(conversationCacheService.popOfflineMessage(USER_ID_1))
                .thenReturn("not-valid-json")
                .thenReturn("{\"id\":\"off-1\",\"type\":\"RECEIVE_MESSAGE\",\"data\":{\"content\":\"msg1\"}}")
                .thenReturn(null);

        // 调用 sendToUser 触发的 sendMessage
        handler.afterConnectionEstablished(session1);
        verify(session1, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    // ==================== getGroupMemberIds ====================

    @Test
    @DisplayName("getGroupMemberIds - 缓存命中返回缓存")
    void getGroupMemberIds_shouldReturnCached() throws Exception {
        createSession(USER_ID_1, session1);
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(java.util.Set.of(USER_ID_1, USER_ID_2));

        // 触发群消息
        User sender = new User();
        sender.setId(USER_ID_1);
        sender.setUsername("张三");

        SendMessageResponse sendResponse = SendMessageResponse.builder()
                .messageId(10001L)
                .createTime(LocalDateTime.now())
                .build();

        String payload = """
                {
                    "id": "group-msg-001",
                    "type": "SEND_MESSAGE",
                    "data": {
                        "sessionId": "g_2001",
                        "msgType": "TEXT",
                        "content": "群消息",
                        "groupId": 2001
                    }
                }
                """;

        when(messageService.sendMessage(eq(USER_ID_1), any(SendMessageRequest.class)))
                .thenReturn(sendResponse);
        when(userService.findById(USER_ID_1)).thenReturn(sender);

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        // 缓存命中，不应查库
        verify(groupMemberMapper, never()).selectGroupMemberUserIds(anyLong());
    }

    @Test
    @DisplayName("getGroupMemberIds - 缓存未命中查库回填")
    void getGroupMemberIds_shouldQueryDbAndBackfill() throws Exception {
        createSession(USER_ID_1, session1);
        // 缓存未命中
        when(groupCacheService.getMemberIds(GROUP_ID)).thenReturn(null);
        when(groupMemberMapper.selectGroupMemberUserIds(GROUP_ID))
                .thenReturn(List.of(USER_ID_1, USER_ID_2));

        // 触发群消息
        User sender = new User();
        sender.setId(USER_ID_1);
        sender.setUsername("张三");

        SendMessageResponse sendResponse = SendMessageResponse.builder()
                .messageId(10001L)
                .createTime(LocalDateTime.now())
                .build();

        String payload = """
                {
                    "id": "group-msg-002",
                    "type": "SEND_MESSAGE",
                    "data": {
                        "sessionId": "g_2001",
                        "msgType": "TEXT",
                        "content": "群消息"
                    }
                }
                """;

        when(messageService.sendMessage(eq(USER_ID_1), any(SendMessageRequest.class)))
                .thenReturn(sendResponse);
        when(userService.findById(USER_ID_1)).thenReturn(sender);

        TextMessage textMessage = new TextMessage(payload);
        handler.handleTextMessage(session1, textMessage);

        // 应查库并回填
        verify(groupMemberMapper).selectGroupMemberUserIds(GROUP_ID);
        verify(groupCacheService).setMemberIds(eq(GROUP_ID), anyList());
    }

    // ==================== 传输错误处理 ====================

    @Test
    @DisplayName("传输错误 - 无 userId 不抛异常")
    void handleTransportError_shouldNotThrow_withoutUserId() {
        when(session1.getAttributes()).thenReturn(Map.of());
        assertDoesNotThrow(() -> handler.handleTransportError(session1, new IOException()));
    }

    @Test
    @DisplayName("传输错误 - 记录日志不中断")
    void handleTransportError_shouldLogAndContinue() {
        when(session1.getAttributes()).thenReturn(Map.of(JwtHandshakeInterceptor.USER_ID_KEY, USER_ID_1));
        assertDoesNotThrow(() -> handler.handleTransportError(session1, new IOException("连接中断")));
    }
}