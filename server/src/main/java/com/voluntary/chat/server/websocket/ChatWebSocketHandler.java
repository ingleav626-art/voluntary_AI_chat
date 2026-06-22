package com.voluntary.chat.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.model.WebSocketMessage;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.MessageResponse;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import com.voluntary.chat.server.service.AiChatService;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final GroupMemberMapper groupMemberMapper;
  private final AiChatService aiChatService;
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

    // 踢掉旧连接：通知旧客户端被顶号，再关闭旧连接
    WebSocketSession oldSession = ONLINE_SESSIONS.put(userId, session);
    if (oldSession != null && oldSession.isOpen()) {
      try {
        WebSocketMessage kickMsg = WebSocketMessage.builder()
            .id(String.valueOf(System.currentTimeMillis()))
            .type(MessageTypes.FORCE_LOGOUT)
            .data(Map.of("reason", "您的账号在其他设备登录"))
            .build();
        String payload = objectMapper.writeValueAsString(kickMsg);
        oldSession.sendMessage(new TextMessage(payload));
      } catch (IOException e) {
        log.warn("通知旧连接顶号失败: userId={}", userId, e);
      }
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
    } catch (JsonProcessingException e) {
      log.warn("WebSocket 消息解析失败: userId={}, error={}", userId, e.getMessage());
      return;
    }

    switch (wsMessage.getType()) {
      case MessageTypes.SEND_MESSAGE -> handleSendMessage(userId, wsMessage);
      case MessageTypes.AI_CHAT -> handleAiChat(userId, wsMessage);
      case MessageTypes.PING -> handlePing(session, wsMessage);
      case MessageTypes.RECONNECT -> handleReconnect(userId, wsMessage);
      default -> log.warn("未知的消息类型: type={}, userId={}", wsMessage.getType(), userId);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Long userId = getUserId(session);
    if (userId != null) {
      // 只移除当前活跃的连接，避免旧连接关闭时误删新连接
      ONLINE_SESSIONS.remove(userId, session);
      // 检查是否真的是最后一个连接断开才广播离线
      if (!ONLINE_SESSIONS.containsKey(userId)) {
        log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
        broadcastStatusChange(userId, false);
      }
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    Long userId = getUserId(session);
    log.error("WebSocket 传输错误: userId={}", userId, exception);
  }

  /**
   * 处理发送消息
   * 持久化消息后转发给目标用户（单聊）或群组成员（群聊）
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

    // 先构建发送者信息（群聊和单聊都用到）
    User sender = userService.findById(senderId);

    if (request.getSessionId().startsWith("g_")) {
      // 群聊消息：广播给所有在线群成员
      broadcastGroupMessage(senderId, sender, request, response);
    } else {
      // 单聊消息：转发给目标用户
      forwardPrivateMessage(senderId, sender, request, response);
    }
  }

  /**
   * 广播群消息给所有在线群成员（除发送者外）
   */
  private void broadcastGroupMessage(Long senderId, User sender,
      SendMessageRequest request, SendMessageResponse response) {
    // 解析群组ID
    String[] parts = request.getSessionId().split("_");
    Long groupId = Long.parseLong(parts[1]);

    // 查询群所有成员的userId
    List<Long> groupMembers = groupMemberMapper.selectGroupMemberUserIds(groupId);

    // 构建 GROUP_MESSAGE 推送
    WebSocketMessage groupMsg = WebSocketMessage.builder()
        .id(String.valueOf(response.getMessageId()))
        .type(MessageTypes.GROUP_MESSAGE)
        .data(Map.of(
            "messageId", response.getMessageId(),
            "sessionId", request.getSessionId(),
            "groupId", groupId,
            "senderId", senderId,
            "senderName", sender.getUsername(),
            "senderAvatar", sender.getAvatar() != null ? sender.getAvatar() : "",
            "senderType", SenderType.USER.name(),
            "msgType", request.getType(),
            "content", request.getContent(),
            "createTime", response.getCreateTime().toString()))
        .build();

    // 广播给所有在线的群成员（排除发送者自己）
    for (Long memberId : groupMembers) {
      if (!memberId.equals(senderId)) {
        sendToUser(memberId, groupMsg);
      }
    }

    log.info("群消息广播: groupId={}, senderId={}, memberCount={}",
        groupId, senderId, groupMembers.size());
  }

  /**
   * 转发单聊消息给目标用户
   */
  private void forwardPrivateMessage(Long senderId, User sender,
      SendMessageRequest request, SendMessageResponse response) {
    // 构建接收消息推送
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

    // 转发消息给目标用户
    Long targetUserId = resolveTargetUserId(senderId, request.getSessionId());
    if (targetUserId != null) {
      sendToUser(targetUserId, receiveMsg);
    }

    log.info("私聊消息转发: senderId={}, targetUserId={}, sessionId={}",
        senderId, targetUserId, request.getSessionId());
  }

  /**
   * 处理 AI 对话请求
   */
  private void handleAiChat(Long userId, WebSocketMessage wsMessage) {
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) wsMessage.getData();

    Long aiId = Long.parseLong(String.valueOf(data.get("aiId")));
    String sessionId = (String) data.get("sessionId");
    String content = (String) data.get("content");

    log.info("AI 对话请求: userId={}, aiId={}, sessionId={}", userId, aiId, sessionId);

    // 异步处理 AI 对话（流式输出）
    aiChatService.handleAiChat(userId, aiId, sessionId, content, wsMessage.getId());
  }

  /**
   * 推送 AI 流式输出
   */
  public void sendAiStream(Long userId, String messageId, String content, boolean done) {
    sendAiStream(userId, messageId, content, done, null);
  }

  /**
   * 推送 AI 流式输出（带完整消息ID）
   */
  public void sendAiStream(Long userId, String messageId, String content, boolean done, Long aiMessageId) {
    Map<String, Object> data = new java.util.LinkedHashMap<>();
    data.put("messageId", messageId);
    data.put("content", content);
    data.put("done", done);
    if (aiMessageId != null) {
      data.put("aiMessageId", aiMessageId);
    }

    WebSocketMessage streamMsg = WebSocketMessage.builder()
        .id(messageId)
        .type(MessageTypes.AI_STREAM)
        .data(data)
        .build();
    sendToUser(userId, streamMsg);
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
   * 处理断线重连
   * 根据 lastMessageId 补发离线期间的消息，并回复 RECONNECT_ACK
   */
  private void handleReconnect(Long userId, WebSocketMessage wsMessage) {
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) wsMessage.getData();
    Object lastMsgIdObj = data.get("lastMessageId");
    if (lastMsgIdObj == null) {
      log.warn("RECONNECT 请求缺少 lastMessageId: userId={}", userId);
      return;
    }

    Long lastMessageId;
    try {
      lastMessageId = Long.parseLong(String.valueOf(lastMsgIdObj));
    } catch (NumberFormatException e) {
      log.warn("RECONNECT lastMessageId 格式错误: userId={}, value={}", userId, lastMsgIdObj);
      return;
    }

    // 查询离线消息
    List<MessageResponse> offlineMessages = messageService.getOfflineMessages(userId, lastMessageId);

    // 逐条推送离线消息
    for (MessageResponse msg : offlineMessages) {
      String wsType = msg.getSessionId().startsWith("g_")
          ? MessageTypes.GROUP_MESSAGE
          : MessageTypes.RECEIVE_MESSAGE;

      Map<String, Object> msgData = new java.util.LinkedHashMap<>();
      msgData.put("messageId", msg.getMessageId());
      msgData.put("sessionId", msg.getSessionId());
      msgData.put("senderId", msg.getSenderId());
      msgData.put("senderName", msg.getSenderName());
      msgData.put("senderAvatar", msg.getSenderAvatar() != null ? msg.getSenderAvatar() : "");
      msgData.put("senderType", msg.getSenderType());
      msgData.put("msgType", msg.getType());
      msgData.put("content", msg.getContent());
      msgData.put("createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : "");

      if (msg.getSessionId().startsWith("g_")) {
        String[] parts = msg.getSessionId().split("_");
        msgData.put("groupId", Long.parseLong(parts[1]));
      }

      WebSocketMessage offlineMsg = WebSocketMessage.builder()
          .id(String.valueOf(msg.getMessageId()))
          .type(wsType)
          .data(msgData)
          .build();
      sendToUser(userId, offlineMsg);
    }

    // 发送 RECONNECT_ACK 确认
    WebSocketMessage ack = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.RECONNECT_ACK)
        .data(Map.of("missedCount", offlineMessages.size()))
        .build();
    sendToUser(userId, ack);

    log.info("断线重连补发完成: userId={}, missedCount={}", userId, offlineMessages.size());
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

  /**
   * 向群内所有在线成员广播消息（排除指定用户）
   *
   * @param sessionId     群会话ID，格式 g_{groupId}
   * @param excludeUserId 排除的用户ID（通常是操作者自己）
   * @param message       WebSocket 消息
   */
  public void broadcastToGroupExcept(String sessionId, Long excludeUserId, WebSocketMessage message) {
    if (!sessionId.startsWith("g_")) {
      return;
    }
    Long groupId = Long.parseLong(sessionId.split("_")[1]);
    List<Long> memberIds = groupMemberMapper.selectGroupMemberUserIds(groupId);
    for (Long memberId : memberIds) {
      if (!memberId.equals(excludeUserId)) {
        sendToUser(memberId, message);
      }
    }
  }

  /**
   * 广播群成员加入通知
   *
   * @param groupId  群组ID
   * @param userId   加入的用户ID
   * @param username 用户名
   * @param avatar   用户头像
   */
  public void broadcastMemberJoin(Long groupId, Long userId, String username, String avatar) {
    WebSocketMessage message = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.GROUP_MEMBER_JOIN)
        .data(Map.of(
            "groupId", groupId,
            "userId", userId,
            "username", username,
            "avatar", avatar != null ? avatar : ""))
        .build();
    broadcastToGroup("g_" + groupId, message);
  }

  /**
   * 广播群成员离开通知
   *
   * @param groupId  群组ID
   * @param userId   离开的用户ID
   * @param username 用户名
   */
  public void broadcastMemberLeave(Long groupId, Long userId, String username) {
    WebSocketMessage message = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.GROUP_MEMBER_LEAVE)
        .data(Map.of(
            "groupId", groupId,
            "userId", userId,
            "username", username))
        .build();
    broadcastToGroup("g_" + groupId, message);
  }

  /**
   * 广播群成员角色变更通知
   *
   * @param groupId  群组ID
   * @param userId   角色变更的用户ID
   * @param username 用户名
   * @param role     新角色（OWNER/ADMIN/MEMBER）
   */
  public void broadcastRoleChange(Long groupId, Long userId, String username, String role) {
    WebSocketMessage message = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.GROUP_MEMBER_ROLE_CHANGE)
        .data(Map.of(
            "groupId", groupId,
            "userId", userId,
            "username", username,
            "role", role))
        .build();
    broadcastToGroup("g_" + groupId, message);
  }

  /**
   * 广播群信息变更通知
   *
   * @param groupId      群组ID
   * @param name         新的群名称（可 null）
   * @param announcement 新的群公告（可 null）
   */
  public void broadcastGroupInfoChange(Long groupId, String name, String announcement) {
    Map<String, Object> data = new java.util.LinkedHashMap<>();
    data.put("groupId", groupId);
    if (name != null)
      data.put("name", name);
    if (announcement != null)
      data.put("announcement", announcement);

    WebSocketMessage message = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.GROUP_INFO_CHANGE)
        .data(data)
        .build();
    broadcastToGroup("g_" + groupId, message);
  }

  /**
   * 广播群组解散通知
   *
   * @param groupId 群组ID
   */
  public void broadcastGroupDismissed(Long groupId) {
    WebSocketMessage message = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.GROUP_DISMISSED)
        .data(Map.of("groupId", groupId))
        .build();
    broadcastToGroup("g_" + groupId, message);
  }

  /**
   * 向群内所有在线成员广播消息（包含所有成员）
   */
  private void broadcastToGroup(String sessionId, WebSocketMessage message) {
    if (!sessionId.startsWith("g_")) {
      return;
    }
    Long groupId = Long.parseLong(sessionId.split("_")[1]);
    List<Long> memberIds = groupMemberMapper.selectGroupMemberUserIds(groupId);
    for (Long memberId : memberIds) {
      sendToUser(memberId, message);
    }
  }

  private Long getUserId(WebSocketSession session) {
    Object userId = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_KEY);
    return userId != null ? (Long) userId : null;
  }
}
