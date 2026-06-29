package org.example.client.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.example.client.engine.AiStreamCallback;
import org.example.client.engine.LocalAiEngine;
import org.example.client.model.AiGroupConfig;
import org.example.client.model.AiProfile;
import org.example.client.model.ConversationInfo;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voluntary.chat.common.constant.MessageTypes;

import javafx.application.Platform;

/**
 * 消息发送服务（提取自 ChatViewModel）
 *
 * <p>
 * 处理消息发送逻辑，包括：
 * - AI对话（本地引擎）
 * - 真人消息（WebSocket）
 * - 群聊@触发AI
 * </p>
 */
public final class MessageSender {

    private static final Logger LOG = LoggerFactory.getLogger(MessageSender.class);

    /** 消息类型 - 文本 */
    private static final String MSG_TYPE_TEXT = "TEXT";

    /** 单例 */
    private static final MessageSender INSTANCE = new MessageSender();

    public static MessageSender getInstance() {
        return INSTANCE;
    }

    /**
     * 发送消息
     *
     * @param currentUser    当前用户
     * @param conversation   当前会话
     * @param text           消息文本
     * @param messages       消息列表（用于添加乐观消息和AI回复）
     * @param pendingMessages 待确认消息映射
     */
    public void sendMessage(
            final UserInfo currentUser,
            final ConversationInfo conversation,
            final String text,
            final javafx.collections.ObservableList<MessageInfo> messages,
            final Map<String, MessageInfo> pendingMessages) {

        if (text == null || text.trim().isEmpty()) {
            return;
        }

        if (conversation == null) {
            return;
        }

        // 生成客户端消息ID用于 ACK 匹配
        final String clientId = UUID.randomUUID().toString();

        // 创建乐观消息（先显示在界面上）
        final MessageInfo optimisticMsg = new MessageInfo();
        optimisticMsg.setMessageId(-1L);
        optimisticMsg.setSessionId(conversation.getSessionId());
        optimisticMsg.setSenderId(currentUser != null ? currentUser.getUserId() : null);
        optimisticMsg.setSenderName(currentUser != null ? currentUser.getUsername() : "");
        optimisticMsg.setSenderType("USER");
        optimisticMsg.setType(MSG_TYPE_TEXT);
        optimisticMsg.setContent(text.trim());
        optimisticMsg.setCreateTime(LocalDateTime.now());
        optimisticMsg.setSentByMe(true);

        messages.add(optimisticMsg);
        pendingMessages.put(clientId, optimisticMsg);

        // 通过 WebSocket 发送
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", conversation.getSessionId());
        data.put("msgType", MSG_TYPE_TEXT);
        data.put("content", text.trim());

        if ("AI".equals(conversation.getTargetType())) {
            // AI 对话始终使用本地引擎（API.md 架构：客户包不走 HTTP/WS 回环）
            handleAiChat(currentUser, conversation, text.trim(), messages);
        } else {
            WebSocketClient.getInstance().send(MessageTypes.SEND_MESSAGE, data);

            // 本地模式下：群聊消息检查@触发AI（服务端跳过@触发检查）
            if (conversation.getSessionId() != null && conversation.getSessionId().startsWith("g_")) {
                triggerGroupAiIfNeeded(currentUser, conversation, text.trim(), messages);
            }
        }

        LOG.info("消息已发送: sessionId={}", conversation.getSessionId());
    }

    /**
     * 处理AI对话（本地引擎）
     */
    private void handleAiChat(
            final UserInfo currentUser,
            final ConversationInfo conversation,
            final String content,
            final javafx.collections.ObservableList<MessageInfo> messages) {

        final Long aiId = conversation.getTargetId();
        final Long userId = currentUser != null ? currentUser.getUserId() : 0L;
        final String sessionId = conversation.getSessionId();

        LocalAiEngine.getInstance().chat(aiId, userId, content, new AiStreamCallback() {
            private final StringBuilder fullContent = new StringBuilder();

            @Override
            public void onChunk(String chunk) {
                fullContent.append(chunk);
                // 流式更新最后一条 AI 消息
                Platform.runLater(() -> {
                    // 查找是否已有 AI 回复消息
                    MessageInfo lastAiMsg = null;
                    for (MessageInfo msg : messages) {
                        if (msg.getMessageId() != null && msg.getMessageId() < 0
                                && msg.getSenderType() != null && "AI".equals(msg.getSenderType())) {
                            lastAiMsg = msg;
                            break;
                        }
                    }
                    if (lastAiMsg != null) {
                        lastAiMsg.setContent(fullContent.toString());
                        final int idx = messages.indexOf(lastAiMsg);
                        if (idx >= 0) {
                            messages.set(idx, lastAiMsg);
                        }
                    }
                });
            }

            @Override
            public void onComplete(String fullResponse, Long messageId) {
                Platform.runLater(() -> {
                    // 创建完整的 AI 回复消息
                    final MessageInfo aiMsg = new MessageInfo();
                    aiMsg.setMessageId(messageId != null ? messageId : -1L);
                    aiMsg.setSessionId(sessionId);
                    aiMsg.setSenderId(aiId);
                    aiMsg.setSenderName(conversation.getTargetName());
                    aiMsg.setSenderType("AI");
                    aiMsg.setType(MSG_TYPE_TEXT);
                    aiMsg.setContent(fullResponse);
                    aiMsg.setCreateTime(LocalDateTime.now());
                    aiMsg.setSentByMe(false);

                    // 移除占位消息
                    messages.removeIf(msg -> msg.getMessageId() != null
                            && msg.getMessageId() < 0 && "AI".equals(msg.getSenderType()));

                    messages.add(aiMsg);
                    LOG.info("AI 回复完成（本地）: aiId={}, sessionId={}", aiId, sessionId);
                });
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    LOG.warn("AI 回复失败（本地）: error={}", error);
                    // 生成更友好的错误提示
                    String userMsg;
                    if (error != null && error.contains("authentication_error")) {
                        userMsg = "API Key 无效，请在 AI 设置中更新有效的 API Key";
                    } else if (error != null && error.contains("invalid_request_error")) {
                        userMsg = "API 请求失败，请检查 AI 角色的模型配置是否正确";
                    } else {
                        userMsg = "AI 回复失败: " + error;
                    }
                    // 更新占位消息为错误提示
                    for (MessageInfo msg : messages) {
                        if (msg.getMessageId() != null && msg.getMessageId() < 0
                                && "AI".equals(msg.getSenderType())) {
                            msg.setContent(userMsg);
                            final int idx = messages.indexOf(msg);
                            if (idx >= 0) {
                                messages.set(idx, msg);
                            }
                            break;
                        }
                    }
                });
            }
        });
    }

    /**
     * 本地模式下群聊@触发AI
     *
     * <p>
     * 本地模式下服务端跳过@触发检查，客户端需要自行处理：
     * 1. 检查消息是否包含@AI名称
     * 2. 获取群AI配置列表
     * 3. 匹配AI名称并触发本地AI引擎
     * 4. 通过GROUP_AI_STREAM消息请求服务端广播
     * </p>
     */
    private void triggerGroupAiIfNeeded(
            final UserInfo currentUser,
            final ConversationInfo conversation,
            final String content,
            final javafx.collections.ObservableList<MessageInfo> messages) {

        if (content == null || !content.contains("@")) {
            return;
        }

        // 解析群ID
        final String sessionId = conversation.getSessionId();
        if (!sessionId.startsWith("g_")) {
            return;
        }
        final String[] parts = sessionId.split("_");
        final Long groupId = Long.parseLong(parts[1]);

        // 获取群AI配置列表
        final AiService aiService = AiService.getInstance();
        aiService.getGroupConfigs(groupId)
                .thenAccept(response -> {
                    if (response == null || !response.isSuccess() || response.getData() == null) {
                        return;
                    }

                    final java.util.List<AiGroupConfig> configs = response.getData();
                    if (configs.isEmpty()) {
                        return;
                    }

                    // 获取本地AI列表用于名称匹配
                    final Long userId = currentUser != null ? currentUser.getUserId() : 0L;
                    final java.util.List<com.voluntary.chat.server.entity.AiProfile> localProfiles =
                            LocalAiEngine.getInstance().listAiProfiles(userId);

                    // 构建 AI ID -> 名称映射
                    final java.util.Map<Long, String> aiNameMap = new java.util.HashMap<>();
                    for (final com.voluntary.chat.server.entity.AiProfile p : localProfiles) {
                        aiNameMap.put(p.getId(), p.getName());
                    }

                    // 检查每个配置是否匹配@
                    for (final AiGroupConfig config : configs) {
                        if (config.getAiId() == null || !Boolean.TRUE.equals(config.getIsEnabled())) {
                            continue;
                        }

                        final Long aiId = config.getAiId();
                        final String aiName = aiNameMap.get(aiId);
                        if (aiName == null) {
                            continue;
                        }

                        // 检查消息是否@该AI
                        if (content.contains("@") && content.contains(aiName)) {
                            LOG.info("群聊@触发AI: groupId={}, aiId={}, aiName={}", groupId, aiId, aiName);
                            triggerLocalGroupAi(currentUser, groupId, aiId, aiName, content, messages);
                            break; // 只触发第一个匹配的AI
                        }
                    }
                })
                .exceptionally(ex -> {
                    LOG.warn("获取群AI配置失败: groupId={}", groupId, ex);
                    return null;
                });
    }

    /**
     * 触发本地群AI回复
     */
    private void triggerLocalGroupAi(
            final UserInfo currentUser,
            final Long groupId,
            final Long aiId,
            final String aiName,
            final String content,
            final javafx.collections.ObservableList<MessageInfo> messages) {

        final Long userId = currentUser != null ? currentUser.getUserId() : 0L;
        final String sessionId = "g_" + groupId + "_a_" + aiId;

        // 过滤@AI名称：移除 "@{aiName}" 部分，只发送实际内容给AI
        String filteredContent = content;
        if (content.contains("@")) {
            // 正则匹配 "@AI名称" 并移除（使用Pattern.quote防止AI名称含特殊正则字符）
            String escapedAiName = aiName.replace(" ", "\\s+");
            String pattern = "@" + java.util.regex.Pattern.quote(escapedAiName) + "\\s*";
            filteredContent = content.replaceAll(pattern, "").trim();
            LOG.info("群聊@AI过滤: aiName={}, 原内容={}, 过滤后={}", aiName, content, filteredContent);
            // 如果过滤后为空，保留原始内容（防止用户只输入@AI名称）
            if (filteredContent.isEmpty()) {
                filteredContent = content;
                LOG.warn("群聊@AI过滤后为空，保留原内容: {}", content);
            }
        }

        // 获取AI头像
        final com.voluntary.chat.server.entity.AiProfile profile =
                LocalAiEngine.getInstance().getAiProfile(aiId);
        final String aiAvatar = profile != null ? profile.getAvatar() : "";

        // 创建占位消息（供流式更新显示）
        final MessageInfo placeholderMsg = new MessageInfo();
        placeholderMsg.setMessageId(-1L);
        placeholderMsg.setSessionId(sessionId);
        placeholderMsg.setSenderId(aiId);
        placeholderMsg.setSenderName(aiName);
        placeholderMsg.setSenderType("AI");
        placeholderMsg.setType(MSG_TYPE_TEXT);
        placeholderMsg.setContent("正在思考...");
        placeholderMsg.setCreateTime(LocalDateTime.now());
        placeholderMsg.setSentByMe(false);

        Platform.runLater(() -> messages.add(placeholderMsg));

        // 使用自定义 sessionId 保存群聊 AI 消息（支持聊天记录保存）
        LocalAiEngine.getInstance().chat(aiId, userId, sessionId, filteredContent, new AiStreamCallback() {
            private final StringBuilder fullContent = new StringBuilder();

            @Override
            public void onChunk(String chunk) {
                fullContent.append(chunk);
                // 只在本地更新发送者界面（流式效果），不广播给群成员
                Platform.runLater(() -> {
                    placeholderMsg.setContent(fullContent.toString());
                    final int idx = messages.indexOf(placeholderMsg);
                    if (idx >= 0) {
                        messages.set(idx, placeholderMsg);
                    }
                });
            }

            @Override
            public void onComplete(String fullResponse, Long aiMessageId) {
                Platform.runLater(() -> {
                    // 移除占位消息
                    messages.remove(placeholderMsg);

                    // 发送GROUP_AI_STREAM广播完整回复给群成员（只发送最后一条完整内容）
                    final Map<String, Object> data = new HashMap<>();
                    data.put("sessionId", sessionId);
                    data.put("groupId", groupId);
                    data.put("aiId", aiId);
                    data.put("aiName", aiName);
                    data.put("aiAvatar", aiAvatar);
                    data.put("content", fullResponse);
                    data.put("done", true);
                    if (aiMessageId != null) {
                        data.put("aiMessageId", aiMessageId);
                    }

                    WebSocketClient.getInstance().send(MessageTypes.GROUP_AI_STREAM, data);

                    // 本地添加完整AI消息（供发送者自己查看）
                    final MessageInfo aiMsg = new MessageInfo();
                    aiMsg.setMessageId(aiMessageId != null ? aiMessageId : -1L);
                    aiMsg.setSessionId(sessionId);
                    aiMsg.setSenderId(aiId);
                    aiMsg.setSenderName(aiName);
                    aiMsg.setSenderType("AI");
                    aiMsg.setType(MSG_TYPE_TEXT);
                    aiMsg.setContent(fullResponse);
                    aiMsg.setCreateTime(LocalDateTime.now());
                    aiMsg.setSentByMe(false);

                    messages.add(aiMsg);
                    LOG.info("群聊AI回复完成（本地）: groupId={}, aiId={}", groupId, aiId);
                });
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    LOG.warn("群聊AI回复失败（本地）: groupId={}, aiId={}, error={}", groupId, aiId, error);

                    // 发送错误提示给群成员
                    final Map<String, Object> data = new HashMap<>();
                    data.put("sessionId", sessionId);
                    data.put("groupId", groupId);
                    data.put("aiId", aiId);
                    data.put("aiName", aiName);
                    data.put("aiAvatar", aiAvatar);
                    data.put("content", "AI 回复失败: " + error);
                    data.put("done", true);

                    WebSocketClient.getInstance().send(MessageTypes.GROUP_AI_STREAM, data);
                });
            }
        });
    }
}