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
import com.voluntary.chat.server.service.MessageService;
import com.voluntary.chat.server.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    public ChatWebSocketHandler(
            final AiChatService aiChatService,
            final AiGroupConfigService aiGroupConfigService,
            final ObjectMapper objectMapper,
            final MessageService messageService,
            final UserService userService,
            final GroupMemberMapper groupMemberMapper) {
        super(aiChatService, aiGroupConfigService, objectMapper);
        this.messageService = messageService;
        this.userService = userService;
        this.groupMemberMapper = groupMemberMapper;
    }

    @Override
    protected void handleUnknownMessage(final Long userId, final WebSocketMessage message) {
        switch (message.getType()) {
            case MessageTypes.SEND_MESSAGE -> handleSendMessage(userId, message);
            case MessageTypes.RECONNECT -> handleReconnect(userId, message);
            default -> super.handleUnknownMessage(userId, message);
        }
    }

    // ======================== 发送消息 ========================

    private void handleSendMessage(final Long senderId, final WebSocketMessage wsMessage) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) wsMessage.getData();

        final SendMessageRequest request = new SendMessageRequest();
        request.setSessionId((String) data.get("sessionId"));
        request.setType((String) data.get("msgType"));
        request.setContent((String) data.get("content"));

        final SendMessageResponse response = messageService.sendMessage(senderId, request);

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

        final User sender = userService.findById(senderId);

        if (request.getSessionId().startsWith("g_")) {
            broadcastGroupMessage(senderId, sender, request, response);
        } else {
            forwardPrivateMessage(senderId, sender, request, response);
        }
    }

    private void broadcastGroupMessage(final Long senderId, final User sender,
            final SendMessageRequest request, final SendMessageResponse response) {
        final String[] parts = request.getSessionId().split("_");
        final Long groupId = Long.parseLong(parts[1]);

        final List<Long> groupMembers = groupMemberMapper.selectGroupMemberUserIds(groupId);

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
            }
        }

        log.info("群消息广播: groupId={}, senderId={}, memberCount={}", groupId, senderId, groupMembers.size());

        triggerGroupAi(groupId, senderId, request.getContent());
    }

    private void forwardPrivateMessage(final Long senderId, final User sender,
            final SendMessageRequest request, final SendMessageResponse response) {
        final WebSocketMessage receiveMsg = WebSocketMessage.builder()
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

        final Long targetUserId = resolveTargetUserId(senderId, request.getSessionId());
        if (targetUserId != null) {
            sendToUser(targetUserId, receiveMsg);
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
        final List<Long> memberIds = groupMemberMapper.selectGroupMemberUserIds(groupId);
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
        final List<Long> memberIds = groupMemberMapper.selectGroupMemberUserIds(groupId);
        log.debug("[WS-BROADCAST] 广播到群: groupId={}, type={}, memberCount={}",
                groupId, message.getType(), memberIds.size());
        for (final Long memberId : memberIds) {
            sendToUser(memberId, message);
        }
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

    // ======================== 其他工具 ========================

    @Override
    public void afterConnectionClosed(final WebSocketSession session,
            final org.springframework.web.socket.CloseStatus status) {
        final Long userId = getUserId(session);
        if (userId != null) {
            ONLINE_SESSIONS.remove(userId, session);
            if (!ONLINE_SESSIONS.containsKey(userId)) {
                log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
                try {
                    broadcastStatusChange(userId, false);
                } catch (final Exception e) {
                    log.warn("广播离线状态失败: userId={}", userId, e);
                }
            }
        }
    }
}
