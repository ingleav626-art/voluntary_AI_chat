package com.voluntary.chat.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.model.WebSocketMessage;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.service.MessageService;
import com.voluntary.chat.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 消息处理器
 * 处理实时消息转发、心跳、在线状态管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final MessageService messageService;
  private final UserService userService;
  private final ObjectMapper objectMapper;

  /** userId -> WebSocketSession，用于在线状态管理和消息推送 */
  private static final Map<Long, WebSocketSession> ONLINE_SESSIONS = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Long userId = getUserId(session);
    if (userId == null) {
      session.close(CloseStatus.NOT_ACCEPTABLE);
      return;
    }

    // 踢掉旧连接，确保一个用户只有一个 WebSocket 连接
    WebSocketSession oldSession = ONLINE_SESSIONS.put(userId, session);
    if (oldSession != null && oldSession.isOpen()) {
      oldSession.close(CloseStatus.NORMAL);
    }

    log.info("WebSocket 连接建立: userId={}", userId);
    broadcastStatusChange(userId, true);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
    Long userId = getUserId(session);
    if (userId == null) {
      return;
    }

    WebSocketMessage wsMessage;
    try {
      wsMessage = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage.class);
    } catch (Exception e) {
      log.warn("WebSocket 消息解析失败: userId={}, error={}", userId, e.getMessage());
      return;
    }

    switch (wsMessage.getType()) {
      case MessageTypes.SEND_MESSAGE -> handleSendMessage(userId, wsMessage);
      case MessageTypes.PING -> handlePing(session, wsMessage);
      default -> log.warn("未知的消息类型: type={}, userId={}", wsMessage.getType(), userId);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Long userId = getUserId(session);
    if (userId != null) {
      ONLINE_SESSIONS.remove(userId);
      log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
      broadcastStatusChange(userId, false);
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    Long userId = getUserId(session);
    log.error("WebSocket 传输错误: userId={}", userId, exception);
  }

  /**
   * 处理发送消息
   * 持久化消息后转发给目标用户
   */
  private void handleSendMessage(Long senderId, WebSocketMessage wsMessage) {
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) wsMessage.getData();

    SendMessageRequest request = new SendMessageRequest();
    request.setSessionId((String) data.get("sessionId"));
    request.setType((String) data.get("msgType"));
    request.setContent((String) data.get("content"));

    // 持久化消息
    SendMessageResponse response = messageService.sendMessage(senderId, request);

    // 构建接收消息推送
    User sender = userService.findById(senderId);
    WebSocketMessage receiveMsg = WebSocketMessage.builder()
        .id(String.valueOf(response.getMessageId()))
        .type(MessageTypes.RECEIVE_MESSAGE)
        .data(Map.of(
            "messageId", response.getMessageId(),
            "sessionId", request.getSessionId(),
            "senderId", senderId,
            "senderName", sender.getUsername(),
            "senderAvatar", sender.getAvatar() != null ? sender.getAvatar() : "",
            "senderType", SenderType.USER.name(),
            "msgType", request.getType(),
            "content", request.getContent(),
            "createTime", response.getCreateTime().toString()))
        .build();

    // 发送 MESSAGE_ACK 给发送者
    WebSocketMessage ackMsg = WebSocketMessage.builder()
        .id(String.valueOf(response.getMessageId()))
        .type(MessageTypes.MESSAGE_ACK)
        .data(Map.of(
            "clientId", wsMessage.getId(),
            "messageId", response.getMessageId(),
            "createTime", response.getCreateTime().toString()))
        .build();
    sendToUser(senderId, ackMsg);

    // 转发消息给目标用户
    Long targetUserId = resolveTargetUserId(senderId, request.getSessionId());
    if (targetUserId != null) {
      sendToUser(targetUserId, receiveMsg);
    }

    log.info("WebSocket 消息转发: senderId={}, targetUserId={}, sessionId={}",
        senderId, targetUserId, request.getSessionId());
  }

  /**
   * 处理心跳
   */
  private void handlePing(WebSocketSession session, WebSocketMessage wsMessage) throws IOException {
    WebSocketMessage pong = WebSocketMessage.builder()
        .id(wsMessage.getId())
        .type(MessageTypes.PONG)
        .data(Map.of())
        .build();
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
  }

  /**
   * 广播用户在线状态变更
   */
  private void broadcastStatusChange(Long userId, boolean online) {
    WebSocketMessage statusMsg = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.STATUS_CHANGE)
        .data(Map.of(
            "userId", userId,
            "online", online))
        .build();

    // 向所有在线用户广播（简化实现，后续可优化为只推送给好友）
    for (Long onlineUserId : ONLINE_SESSIONS.keySet()) {
      if (!onlineUserId.equals(userId)) {
        sendToUser(onlineUserId, statusMsg);
      }
    }
  }

  /**
   * 向指定用户推送消息
   */
  public void sendToUser(Long userId, WebSocketMessage message) {
    WebSocketSession session = ONLINE_SESSIONS.get(userId);
    if (session != null && session.isOpen()) {
      try {
        String payload = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(payload));
      } catch (IOException e) {
        log.error("WebSocket 消息推送失败: userId={}", userId, e);
      }
    }
  }

  /**
   * 检查用户是否在线
   */
  public boolean isUserOnline(Long userId) {
    WebSocketSession session = ONLINE_SESSIONS.get(userId);
    return session != null && session.isOpen();
  }

  /**
   * 从 sessionId 解析目标用户ID（单聊场景）
   */
  private Long resolveTargetUserId(Long senderId, String sessionId) {
    if (sessionId.startsWith("p_")) {
      String[] parts = sessionId.split("_");
      Long user1 = Long.parseLong(parts[1]);
      Long user2 = Long.parseLong(parts[2]);
      return user1.equals(senderId) ? user2 : user1;
    }
    // 群聊和AI聊天的转发逻辑在后续迭代中实现
    return null;
  }

  private Long getUserId(WebSocketSession session) {
    Object userId = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_KEY);
    return userId != null ? (Long) userId : null;
  }
}
