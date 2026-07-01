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
import com.voluntary.chat.server.service.AiGroupConfigService;
import com.voluntary.chat.server.service.ConversationCacheService;
import com.voluntary.chat.server.service.GroupCacheService;
import com.voluntary.chat.server.service.MessageService;
import com.voluntary.chat.server.service.OnlineStatusService;
import com.voluntary.chat.server.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 消息处理器（server 全量版）
 *
 * <p>
 * 继承 {@link AiWebSocketHandler}，在 AI 处理基础上增加真人消息转发、断线重连、群事件广播等功能。
 * </p>
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends AiWebSocketHandler {

    private final MessageService messageService;
    private final UserService userService;
    private final GroupMemberMapper groupMemberMapper;
    private final OnlineStatusService onlineStatusService;
    private final ConversationCacheService conversationCacheService;
    private final GroupCacheService groupCacheService;

    public ChatWebSocketHandler(
            final AiChatService aiChatService,
            final AiGroupConfigService aiGroupConfigService,
            final ObjectMapper objectMapper,
            final MessageService messageService,
            final UserService userService,
            final GroupMemberMapper groupMemberMapper,
            final OnlineStatusService onlineStatusService,
            final ConversationCacheService conversationCacheService,
            final GroupCacheService groupCacheService) {
        super(aiChatService, aiGroupConfigService, objectMapper);
        this.messageService = messageService;
        this.userService = userService;
        this.groupMemberMapper = groupMemberMapper;
        this.onlineStatusService = onlineStatusService;
        this.conversationCacheService = conversationCacheService;
        this.groupCacheService = groupCacheService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        final Long userId = getUserId(session);
        if (userId != null) {
            onlineStatusService.markOnline(userId);
            log.debug("用户上线: userId={}", userId);
            // 补发离线消息
            replayOfflineMessages(userId);
        }
    }

    /**
     * 补发离线消息 — 从 Redis 队列中消费并发送给用户
     */
    private void replayOfflineMessages(Long userId) {
        int count = 0;
        try {
            String msgJson;
            while ((msgJson = conversationCacheService.popOfflineMessage(userId)) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> msgData = objectMapper.readValue(msgJson, Map.class);
                String type = (String) msgData.get("type");
                WebSocketMessage message = WebSocketMessage.builder()
                        .id((String) msgData.get("id"))
                        .type(type)
                        .data(msgData)
                        .build();
                sendToUser(userId, message);
                count++;
            }
        } catch (Exception e) {
            log.warn("离线消息补发解析失败: userId={}", userId, e);
        }
        if (count > 0) {
            log.info("离线消息补发完成: userId={}, count={}", userId, count);
        }
    }

    @Override
    protected void handleUnknownMessage(final Long userId, final WebSocketMessage message) {
        switch (message.getType()) {
            case MessageTypes.SEND_MESSAGE -> handleSendMessage(userId, message);
            case MessageTypes.RECONNECT -> handleReconnect(userId, message);
            case MessageTypes.GROUP_AI_STREAM -> handleGroupAiStream(userId, message);
            default -> super.handleUnknownMessage(userId, message);
        }
    }

    // ======================== 发送消息 ========================

    /**
     * 处理本地模式下客户端广播的群AI回复
     *
     * <p>
     * 本地模式下AI角色存储在客户端H2数据库，服务端无法直接处理AI对话。
     * 客户端生成AI回复后，通过此消息类型请求服务端广播给群成员。
     * </p>
     */
    private void handleGroupAiStream(final Long userId, final WebSocketMessage wsMessage) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) wsMessage.getData();

        final String sessionId = (String) data.get("sessionId");
        final Long groupId = Long.parseLong(String.valueOf(data.get("groupId")));
        final Long aiId = Long.parseLong(String.valueOf(data.get("aiId")));
        final String aiName = (String) data.get("aiName");
        final String aiAvatar = (String) data.get("aiAvatar");
        final String content = (String) data.get("content");
        final Boolean done = (Boolean) data.get("done");
        final Object aiMessageIdObj = data.get("aiMessageId");
        final Long aiMessageId = aiMessageIdObj != null
                ? Long.parseLong(String.valueOf(aiMessageIdObj)) : null;

        // 构建AI_STREAM消息并广播
        final Map<String, Object> streamData = new LinkedHashMap<>();
        streamData.put("messageId", wsMessage.getId());
        streamData.put("sessionId", sessionId);
        streamData.put("senderId", aiId);
        streamData.put("senderName", aiName);
        streamData.put("senderAvatar", aiAvatar != null ? aiAvatar : "");
        streamData.put("senderType", SenderType.AI.name());
        streamData.put("msgType", "TEXT");
        streamData.put("content", content);
        streamData.put("done", done);
        if (aiMessageId != null) {
            streamData.put("aiMessageId", aiMessageId);
        }
        streamData.put("createTime", java.time.LocalDateTime.now().toString());

        final WebSocketMessage streamMsg = WebSocketMessage.builder()
                .id(wsMessage.getId())
                .type(MessageTypes.AI_STREAM)
                .data(streamData)
                .build();

        // 广播给群成员
        final List<Long> memberIds = getGroupMemberIds(groupId);
        for (final Long memberId : memberIds) {
            sendToUser(memberId, streamMsg);
        }

        log.info("群AI回复广播（本地模式）: groupId={}, aiId={}, done={}, memberCount={}",
                groupId, aiId, done, memberIds.size());
    }

    private void handleSendMessage(final Long senderId, final WebSocketMessage wsMessage) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) wsMessage.getData();

        final SendMessageRequest request = new SendMessageRequest();
        request.setSessionId((String) data.get("sessionId"));
        request.setType((String) data.get("msgType"));
        request.setContent((String) data.get("content"));

        log.info("[MSG-SEND] senderId={}, sessionId={}, type={}, contentLen={}",
                senderId, request.getSessionId(), request.getType(),
                request.getContent() != null ? request.getContent().length() : 0);

        final SendMessageResponse response = messageService.sendMessage(senderId, request);

        // 更新会话缓存：最后一条消息 + 会话列表排序
        conversationCacheService.setLastMessage(request.getSessionId(),
                new ConversationCacheService.LastMessageCache(
                        request.getContent(),
                        java.util.Objects.requireNonNullElse(
                                com.voluntary.chat.common.enums.MessageType.valueOf(request.getType()).ordinal(), 0),
                        response.getCreateTime(),
                        senderId));
        conversationCacheService.updateSessionList(senderId, request.getSessionId(), response.getCreateTime());

        // MESSAGE_ACK
        final WebSocketMessage ackMsg = WebSocketMessage.builder()
                .id(String.valueOf(response.getMessageId()))
                .type(MessageTypes.MESSAGE_ACK)
                .data(Map.of(
                        "clientId", wsMessage.getId(),
                        "messageId", response.getMessageId(),
                        "createTime", response.getCreateTime().toString()))
                .build();
        sendToUser(senderId, ackMsg);

        // 关键修复：容错处理发送方用户不存在的情况
        // 原因：测试阶段可能出现孤儿数据（如解散群时未清理 message 表），
        // 或者用户因数据库重置/迁移导致 message 中存在但 user 中不存在
        // 处理：使用 senderId 构造虚拟 User 对象，保证消息路由不中断
        User sender;
        try {
            sender = userService.findById(senderId);
        } catch (final Exception e) {
            log.warn("发送方用户不存在，使用虚拟用户继续发送: userId={}, error={}",
                    senderId, e.getMessage());
            sender = new User();
            sender.setId(senderId);
            final String idStr = senderId.toString();
            sender.setUsername("用户" + idStr.substring(0, Math.min(4, idStr.length())));
            sender.setAvatar(null);
        }

        if (request.getSessionId().startsWith("g_")) {
            broadcastGroupMessage(senderId, sender, request, response);
        } else {
            forwardPrivateMessage(senderId, sender, request, response);
        }
    }

    /**
     * 获取群成员 ID 列表（优先读缓存，缓存未命中则查库并回填）
     */
    private List<Long> getGroupMemberIds(Long groupId) {
        Set<Long> cached = groupCacheService.getMemberIds(groupId);
        if (cached != null && !cached.isEmpty()) {
            log.debug("群成员缓存命中: groupId={}, count={}", groupId, cached.size());
            return List.copyOf(cached);
        }
        // 缓存未命中，查库
        List<Long> memberIds = groupMemberMapper.selectGroupMemberUserIds(groupId);
        // 异步回填缓存
        try {
            groupCacheService.setMemberIds(groupId, memberIds);
        } catch (Exception e) {
            log.warn("群成员缓存回填失败: groupId={}", groupId, e);
        }
        return memberIds;
    }

    private void broadcastGroupMessage(final Long senderId, final User sender,
            final SendMessageRequest request, final SendMessageResponse response) {
        final String[] parts = request.getSessionId().split("_");
        final Long groupId = Long.parseLong(parts[1]);

        final List<Long> groupMembers = getGroupMemberIds(groupId);

        final WebSocketMessage groupMsg = WebSocketMessage.builder()
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

        for (final Long memberId : groupMembers) {
            if (!memberId.equals(senderId)) {
                sendToUser(memberId, groupMsg);
                conversationCacheService.incrementUnread(memberId, request.getSessionId());
            }
        }

        log.info("群消息广播: groupId={}, senderId={}, memberCount={}", groupId, senderId, groupMembers.size());

        triggerGroupAi(groupId, senderId, sender.getUsername(), request.getContent());
    }

    private void forwardPrivateMessage(final Long senderId, final User sender,
            final SendMessageRequest request, final SendMessageResponse response) {
        final Map<String, Object> msgData = new LinkedHashMap<>();
        msgData.put("messageId", response.getMessageId());
        msgData.put("sessionId", request.getSessionId());
        msgData.put("senderId", senderId);
        msgData.put("senderName", sender.getUsername());
        msgData.put("senderAvatar", sender.getAvatar() != null ? sender.getAvatar() : "");
        msgData.put("senderType", SenderType.USER.name());
        msgData.put("msgType", request.getType());
        msgData.put("content", request.getContent());
        msgData.put("createTime", response.getCreateTime().toString());

        final WebSocketMessage receiveMsg = WebSocketMessage.builder()
                .id(String.valueOf(response.getMessageId()))
                .type(MessageTypes.RECEIVE_MESSAGE)
                .data(msgData)
                .build();

        final Long targetUserId = resolveTargetUserId(senderId, request.getSessionId());
        if (targetUserId != null) {
            // 检查目标是否在线
            if (onlineStatusService.isOnline(targetUserId)) {
                sendToUser(targetUserId, receiveMsg);
            } else {
                // 离线：存入 Redis 队列
                try {
                    Map<String, Object> offlineData = new LinkedHashMap<>(msgData);
                    offlineData.put("type", MessageTypes.RECEIVE_MESSAGE);
                    offlineData.put("id", receiveMsg.getId());
                    String json = objectMapper.writeValueAsString(offlineData);
                    conversationCacheService.pushOfflineMessage(targetUserId, json);
                    log.debug("离线消息已存储: targetUserId={}, messageId={}", targetUserId, response.getMessageId());
                } catch (JsonProcessingException e) {
                    log.warn("离线消息序列化失败: targetUserId={}", targetUserId, e);
                }
            }
            conversationCacheService.incrementUnread(targetUserId, request.getSessionId());
        }

        log.info("私聊消息转发: senderId={}, targetUserId={}, sessionId={}",
                senderId, targetUserId, request.getSessionId());
    }

    // ======================== 断线重连 ========================

    private void handleReconnect(final Long userId, final WebSocketMessage wsMessage) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) wsMessage.getData();
        final Object lastMsgIdObj = data.get("lastMessageId");
        if (lastMsgIdObj == null) {
            log.warn("RECONNECT 请求缺少 lastMessageId: userId={}", userId);
            return;
        }

        final Long lastMessageId;
        try {
            lastMessageId = Long.parseLong(String.valueOf(lastMsgIdObj));
        } catch (final NumberFormatException e) {
            log.warn("RECONNECT lastMessageId 格式错误: userId={}, value={}", userId, lastMsgIdObj);
            return;
        }

        final List<MessageResponse> offlineMessages = messageService.getOfflineMessages(userId, lastMessageId);

        for (final MessageResponse msg : offlineMessages) {
            final String wsType = msg.getSessionId().startsWith("g_")
                    ? MessageTypes.GROUP_MESSAGE
                    : MessageTypes.RECEIVE_MESSAGE;

            final Map<String, Object> msgData = new java.util.LinkedHashMap<>();
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
                final String[] parts = msg.getSessionId().split("_");
                msgData.put("groupId", Long.parseLong(parts[1]));
            }

            final WebSocketMessage offlineMsg = WebSocketMessage.builder()
                    .id(String.valueOf(msg.getMessageId()))
                    .type(wsType)
                    .data(msgData)
                    .build();
            sendToUser(userId, offlineMsg);
        }

        final WebSocketMessage ack = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.RECONNECT_ACK)
                .data(Map.of("missedCount", offlineMessages.size()))
                .build();
        sendToUser(userId, ack);

        log.info("断线重连补发完成: userId={}, missedCount={}", userId, offlineMessages.size());
    }

    // ======================== 广播工具 ========================

    private void broadcastStatusChange(final Long userId, final boolean online) {
        final WebSocketMessage statusMsg = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.STATUS_CHANGE)
                .data(Map.of("userId", userId, "online", online))
                .build();

        for (final Long onlineUserId : ONLINE_SESSIONS.keySet()) {
            if (!onlineUserId.equals(userId)) {
                sendToUser(onlineUserId, statusMsg);
            }
        }
    }

    private Long resolveTargetUserId(final Long senderId, final String sessionId) {
        if (sessionId.startsWith("p_")) {
            final String[] parts = sessionId.split("_");
            final Long user1 = Long.parseLong(parts[1]);
            final Long user2 = Long.parseLong(parts[2]);
            return user1.equals(senderId) ? user2 : user1;
        }
        return null;
    }

    public void broadcastToGroupExcept(final String sessionId, final Long excludeUserId,
            final WebSocketMessage message) {
        if (!sessionId.startsWith("g_")) {
            return;
        }
        final Long groupId = Long.parseLong(sessionId.split("_")[1]);
        final List<Long> memberIds = getGroupMemberIds(groupId);
        for (final Long memberId : memberIds) {
            if (!memberId.equals(excludeUserId)) {
                sendToUser(memberId, message);
            }
        }
    }

    public void broadcastToGroup(final String sessionId, final WebSocketMessage message) {
        if (!sessionId.startsWith("g_")) {
            return;
        }
        final Long groupId = Long.parseLong(sessionId.split("_")[1]);
        final List<Long> memberIds = getGroupMemberIds(groupId);
        log.debug("[WS-BROADCAST] 广播到群: groupId={}, type={}, memberCount={}",
                groupId, message.getType(), memberIds.size());
        for (final Long memberId : memberIds) {
            sendToUser(memberId, message);
        }
    }

    /**
     * 向群聊广播 AI 流式回复
     *
     * @param sessionId 会话ID（格式: g_{groupId}_a_{aiId}）
     * @param messageId 消息ID
     * @param aiId      AI角色ID
     * @param aiName    AI名称
     * @param aiAvatar  AI头像
     * @param content   内容分块
     * @param done      是否结束
     */
    public void sendGroupAiStream(final String sessionId, final String messageId,
            final Long aiId, final String aiName, final String aiAvatar,
            final String content, final boolean done) {
        sendGroupAiStream(sessionId, messageId, aiId, aiName, aiAvatar, content, done, null);
    }

    /**
     * 向群聊广播 AI 流式回复（带消息ID）
     *
     * @param sessionId    会话ID（格式: g_{groupId}_a_{aiId}）
     * @param messageId    消息ID
     * @param aiId         AI角色ID
     * @param aiName       AI名称
     * @param aiAvatar     AI头像
     * @param content      内容分块
     * @param done         是否结束
     * @param aiMessageId  AI消息ID（仅 done=true 时有值）
     */
    public void sendGroupAiStream(final String sessionId, final String messageId,
            final Long aiId, final String aiName, final String aiAvatar,
            final String content, final boolean done, final Long aiMessageId) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", messageId);
        data.put("sessionId", sessionId);
        data.put("senderId", aiId);
        data.put("senderName", aiName);
        data.put("senderAvatar", aiAvatar != null ? aiAvatar : "");
        data.put("senderType", SenderType.AI.name());
        data.put("msgType", "TEXT");
        data.put("content", content);
        data.put("done", done);
        if (aiMessageId != null) {
            data.put("aiMessageId", aiMessageId);
        }
        data.put("createTime", java.time.LocalDateTime.now().toString());

        final WebSocketMessage streamMsg = WebSocketMessage.builder()
                .id(messageId)
                .type(MessageTypes.AI_STREAM)
                .data(data)
                .build();

        final String groupSessionId = extractGroupSessionId(sessionId);
        broadcastToGroup(groupSessionId, streamMsg);

        log.debug("群聊AI流式回复: groupSessionId={}, aiId={}, done={}, contentLen={}",
                groupSessionId, aiId, done, content.length());
    }

    /**
     * 从群AI会话ID中提取群会话ID
     * 输入: g_{groupId}_a_{aiId}
     * 输出: g_{groupId}
     */
    private String extractGroupSessionId(final String sessionId) {
        if (sessionId == null) {
            return null;
        }
        final int aIdx = sessionId.indexOf("_a_");
        if (aIdx > 0) {
            return sessionId.substring(0, aIdx);
        }
        return sessionId;
    }

    public void broadcastMemberJoin(final Long groupId, final Long userId,
            final String username, final String avatar) {
        final WebSocketMessage message = WebSocketMessage.builder()
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

    public void broadcastMemberLeave(final Long groupId, final Long userId,
            final String username, final String reason) {
        final WebSocketMessage message = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.GROUP_MEMBER_LEAVE)
                .data(Map.of(
                        "groupId", groupId,
                        "userId", userId,
                        "username", username,
                        "reason", reason != null ? reason : "LEAVE"))
                .build();
        broadcastToGroup("g_" + groupId, message);
    }

    public void broadcastRoleChange(final Long groupId, final Long userId,
            final String username, final String role) {
        final WebSocketMessage message = WebSocketMessage.builder()
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

    public void broadcastGroupInfoChange(final Long groupId, final String name,
            final String announcement) {
        final Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("groupId", groupId);
        if (name != null)
            data.put("name", name);
        if (announcement != null)
            data.put("announcement", announcement);

        final WebSocketMessage message = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.GROUP_INFO_CHANGE)
                .data(data)
                .build();
        broadcastToGroup("g_" + groupId, message);
    }

    public void broadcastGroupDismiss(final Long groupId) {
        final WebSocketMessage message = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.GROUP_DISMISSED)
                .data(Map.of("groupId", groupId))
                .build();
        broadcastToGroup("g_" + groupId, message);
    }

    // ======================== 通知推送 ========================

    /**
     * 向指定用户推送系统通知
     * <p>
     * 支持多种通知场景：新消息提醒、AI 主动问候、待办提醒、系统事件。
     * 客户端收到后根据 type 显示对应的通知样式（托盘气泡/应用内横幅）。
     * </p>
     *
     * @param userId    目标用户ID
     * @param notifType 通知子类型（NOTIFICATION_NEW_MESSAGE / NOTIFICATION_AI_GREETING 等）
     * @param title     通知标题
     * @param content   通知正文
     * @param sessionId 可选，关联的会话ID，点击通知后跳转
     */
    public void sendNotification(final Long userId, final String notifType,
            final String title, final String content,
            final String sessionId) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("notifType", notifType);
        data.put("title", title);
        data.put("content", content);
        if (sessionId != null) {
            data.put("sessionId", sessionId);
        }

        final WebSocketMessage message = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.NOTIFICATION)
                .data(data)
                .build();
        sendToUser(userId, message);
    }

    /**
     * 向指定用户推送待办提醒通知
     */
    public void sendTodoNotification(final Long userId, final String title,
            final String content) {
        sendNotification(userId, MessageTypes.NOTIFICATION_TODO_REMINDER,
                title, content, null);
    }

    /**
     * 向指定用户推送通知设置已变更通知
     * <p>
     * 当其他设备修改了通知设置时，通知当前设备刷新设置。
     * </p>
     *
     * @param userId 目标用户ID
     */
    public void sendSettingsChangedNotification(final Long userId) {
        final WebSocketMessage message = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.NOTIFICATION_SETTINGS_CHANGED)
                .data(Map.of("message", "通知设置已在其他设备修改"))
                .build();
        sendToUser(userId, message);
    }

    // ======================== 其他工具 ========================

    @Override
    public void afterConnectionClosed(final WebSocketSession session,
            final org.springframework.web.socket.CloseStatus status) {
        final Long userId = getUserId(session);
        if (userId != null) {
            ONLINE_SESSIONS.remove(userId, session);
            if (!ONLINE_SESSIONS.containsKey(userId)) {
                log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
                onlineStatusService.markOffline(userId);
                try {
                    broadcastStatusChange(userId, false);
                } catch (final Exception e) {
                    log.warn("广播离线状态失败: userId={}", userId, e);
                }
            }
        }
    }
}
