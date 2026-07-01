package com.voluntary.chat.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;
import com.voluntary.chat.server.entity.AiGroupConfig;
import com.voluntary.chat.server.service.AiChatService;
import com.voluntary.chat.server.service.AiGroupConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI WebSocket 处理器（ai-core 通用版）
 *
 * <p>
 * 处理 AI 对话、流式推送、心跳。真人消息由子类 {@code ChatWebSocketHandler} 处理。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class AiWebSocketHandler extends TextWebSocketHandler implements AiStreamSender {

    protected final AiChatService aiChatService;
    protected final AiGroupConfigService aiGroupConfigService;
    protected final ObjectMapper objectMapper;

    /** userId -> WebSocketSession */
    protected static final Map<Long, WebSocketSession> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        final Long userId = getUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 踢掉旧连接
        final WebSocketSession oldSession = ONLINE_SESSIONS.put(userId, session);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                final WebSocketMessage kickMsg = WebSocketMessage.builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .type(MessageTypes.FORCE_LOGOUT)
                        .data(Map.of("reason", "您的账号在其他设备登录"))
                        .build();
                final String payload = objectMapper.writeValueAsString(kickMsg);
                oldSession.sendMessage(new TextMessage(payload));
            } catch (final IOException e) {
                log.warn("通知旧连接顶号失败: userId={}", userId, e);
            }
            oldSession.close(CloseStatus.NORMAL);
        }

        log.info("WebSocket 连接建立: userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        final Long userId = getUserId(session);
        if (userId == null) {
            log.warn("WebSocket 收到消息但 userId 为空: sessionId={}", session.getId());
            return;
        }

        final WebSocketMessage wsMessage;
        try {
            wsMessage = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage.class);
        } catch (final JsonProcessingException e) {
            log.warn("WebSocket 消息解析失败: userId={}, error={}, payload={}",
                    userId, e.getMessage(), textMessage.getPayload());
            return;
        }

        log.debug("[WS-RECV] userId={}, type={}, messageId={}",
                userId, wsMessage.getType(), wsMessage.getId());

        switch (wsMessage.getType()) {
            case MessageTypes.AI_CHAT -> handleAiChat(userId, wsMessage);
            case MessageTypes.PING -> handlePing(session);
            default -> handleUnknownMessage(userId, wsMessage);
        }
    }

    /**
     * 子类可重写此方法处理未知消息类型（如真人消息转发）
     */
    protected void handleUnknownMessage(final Long userId, final WebSocketMessage message) {
        log.warn("未知消息类型: type={}, userId={}", message.getType(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        final Long userId = getUserId(session);
        if (userId != null) {
            ONLINE_SESSIONS.remove(userId, session);
            if (!ONLINE_SESSIONS.containsKey(userId)) {
                log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        final Long userId = getUserId(session);
        log.error("WebSocket 传输错误: userId={}", userId, exception);
    }

    // ======================== AI 对话 ========================

    /**
     * 处理 AI 对话请求
     */
    protected void handleAiChat(final Long userId, final WebSocketMessage wsMessage) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) wsMessage.getData();

        final Long aiId = Long.parseLong(String.valueOf(data.get("aiId")));
        final String sessionId = (String) data.get("sessionId");
        final String content = (String) data.get("content");

        log.info("AI 对话请求: userId={}, aiId={}, sessionId={}", userId, aiId, sessionId);

        // AiChatService 内部已通过 AiStreamSender 发送流式回复
        aiChatService.handleAiChat(userId, aiId, sessionId, content, wsMessage.getId());
    }

    // ======================== AiStreamSender 实现 ========================

    @Override
    public void sendAiStream(final Long userId, final String messageId,
            final String content, final boolean done) {
        sendAiStream(userId, messageId, content, done, null);
    }

    @Override
    public void sendAiStream(final Long userId, final String messageId,
            final String content, final boolean done, final Long aiMessageId) {
        final Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("messageId", messageId);
        data.put("content", content);
        data.put("done", done);
        if (aiMessageId != null) {
            data.put("aiMessageId", aiMessageId);
        }

        final WebSocketMessage streamMsg = WebSocketMessage.builder()
                .id(messageId)
                .type(MessageTypes.AI_STREAM)
                .data(data)
                .build();
        sendToUser(userId, streamMsg);
    }

    // ======================== 群聊 AI 触发 ========================

    /**
     * 群聊 AI 触发检查
     */
    protected void triggerGroupAi(final Long groupId, final Long senderId,
            final String senderName, final String content) {
        try {
            final List<AiGroupConfig> enabledConfigs = aiGroupConfigService.getEnabledConfigs(groupId);
            if (enabledConfigs == null || enabledConfigs.isEmpty()) {
                return;
            }

            for (final AiGroupConfig config : enabledConfigs) {
                final Long aiId = config.getAiId();
                if (aiGroupConfigService.checkTrigger(groupId, content, aiId)) {
                    final String messageId = "group_ai_" + System.currentTimeMillis() + "_" + aiId;
                    log.info("群聊AI触发: groupId={}, aiId={}, senderId={}", groupId, aiId, senderId);
                    aiChatService.handleGroupAiChat(groupId, senderId, senderName, aiId, content, messageId);
                }
            }
        } catch (final Exception e) {
            log.warn("群聊AI触发检查异常: groupId={}", groupId, e);
        }
    }

    // ======================== 心跳 ========================

    private void handlePing(final WebSocketSession session) throws IOException {
        final WebSocketMessage pong = WebSocketMessage.builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .type(MessageTypes.PONG)
                .data(Map.of())
                .build();
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    // ======================== 消息推送 ========================

    /**
     * 向指定用户推送消息
     */
    public void sendToUser(final Long userId, final WebSocketMessage message) {
        final WebSocketSession session = ONLINE_SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                final String payload = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(payload));
                log.debug("[WS-SEND] 消息已推送: userId={}, type={}", userId, message.getType());
            } catch (final IOException e) {
                log.error("[WS-SEND] WebSocket 消息推送失败: userId={}, type={}", userId, message.getType(), e);
            }
        } else {
            log.debug("[WS-SEND] 用户不在线，跳过推送: userId={}, type={}", userId, message.getType());
        }
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(final Long userId) {
        final WebSocketSession session = ONLINE_SESSIONS.get(userId);
        return session != null && session.isOpen();
    }

    // ======================== 工具方法 ========================

    protected static Long getUserId(final WebSocketSession session) {
        if (session == null || session.getAttributes() == null) {
            return null;
        }
        return (Long) session.getAttributes().get("userId");
    }
}
