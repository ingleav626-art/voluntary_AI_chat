package org.example.client.view;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.example.client.config.ServerConnectionManager;
import org.example.client.config.ServerMode;
import org.example.client.engine.AiStreamCallback;
import org.example.client.engine.LocalAiEngine;
import org.example.client.model.ConversationInfo;
import org.example.client.model.ImageUploadResponse;
import org.example.client.model.MarkReadRequest;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.example.client.service.ChatService;
import org.example.client.service.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voluntary.chat.common.constant.MessageTypes;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 聊天区域视图模型（MVVM）
 *
 * <p>
 * 管理当前会话的消息列表、消息发送和历史记录加载。
 * </p>
 *
 * <p>
 * <b>TODO:⚠️ 类长度接近限制警告：当前379行，接近Service限制（400行）</b>
 * <br>
 * 请谨慎添加新功能，避免超限。如需扩展，应考虑拆分为：
 * <ul>
 * <li>MessageSender（消息发送逻辑）</li>
 * <li>MessageListManager（消息列表管理）</li>
 * </ul>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ChatViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(ChatViewModel.class);

    /** 消息类型 - 文本 */
    private static final String MSG_TYPE_TEXT = "TEXT";

    /** 默认每页数量 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 当前用户 */
    private final UserInfo currentUser;

    /** 当前会话 */
    private final ConversationInfo conversation;

    /** 消息列表 */
    private final ListProperty<MessageInfo> messages = new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 输入框文本 */
    private final StringProperty inputText = new SimpleStringProperty("");

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 待确认消息（clientId -> MessageInfo），static 确保切换会话后 ACK 仍能找到待处理消息 */
    private static final Map<String, MessageInfo> pendingMessages = new HashMap<>();

    /** 待撤回消息的 clientId 集合（ACK 到达后自动执行 REST 撤回），static 确保跨 ChatViewModel 实例共享 */
    private static final java.util.Set<String> pendingRecallClientIds = new java.util.HashSet<>();

    /** 待撤回消息的内容集合（用于刷新后匹配执行延迟撤回），static 确保跨 ChatViewModel 实例共享 */
    private static final java.util.Set<String> pendingRecallContents = new java.util.HashSet<>();

    /** 待注入的系统消息（sessionId → 消息列表），用于群成员加入等场景 */
    private static final Map<String, List<MessageInfo>> pendingSystemMessages = new ConcurrentHashMap<>();

    /** 当前页码 */
    private int currentPage = 1;

    public ChatViewModel(final UserInfo currentUser, final ConversationInfo conversation) {
        this.currentUser = currentUser;
        this.conversation = conversation;
    }

    /**
     * 加载历史消息
     */
    public void loadHistory() {
        if (conversation == null) {
            LOG.warn("loadHistory: 当前会话为空，跳过加载");
            return;
        }

        final String sessionId = conversation.getSessionId();
        loading.set(true);
        currentPage = 1;
        hasMoreHistory = true;
        LOG.info("开始加载历史消息: sessionId={}", sessionId);

        ChatService.getInstance().getHistory(sessionId, currentPage, DEFAULT_PAGE_SIZE)
                .thenAccept(response -> {
                    // 异步回调在 HTTP 线程执行，UI 更新必须切回 JavaFX 线程
                    Platform.runLater(() -> {
                        loading.set(false);

                        // 先注入待处理的系统消息（无论历史加载成功与否都要补入）
                        drainPendingSystemMessages(sessionId);

                        if (response != null && response.isSuccess() && response.getData() != null) {
                            final List<MessageInfo> list = response.getData().getList();
                            if (list != null) {
                                try {
                                    // 保存旧消息的已读状态（服务端返回的消息不含 read 字段）
                                    // 避免 setAll 后本地的已读状态丢失
                                    final java.util.Map<Long, Boolean> oldReadStatus = new java.util.HashMap<>();
                                    for (final MessageInfo old : messages) {
                                        if (old.getMessageId() != null && old.getMessageId() > 0) {
                                            oldReadStatus.put(old.getMessageId(), old.isRead());
                                        }
                                    }

                                    // 标记是否为当前用户发送
                                    for (final MessageInfo msg : list) {
                                        msg.setSentByMe(currentUser != null
                                                && currentUser.getUserId() != null
                                                && currentUser.getUserId().equals(msg.getSenderId()));
                                        // 恢复已读状态（仅在旧消息中有标记时恢复）
                                        final Boolean wasRead = oldReadStatus.get(msg.getMessageId());
                                        if (wasRead != null) {
                                            msg.setRead(wasRead);
                                        }
                                    }
                                    // 按时间升序排序（先发的在上方），服务端默认按时间倒序返回
                                    list.sort(java.util.Comparator.comparing(
                                            MessageInfo::getCreateTime,
                                            java.util.Comparator.nullsFirst(java.time.LocalDateTime::compareTo)));
                                    messages.setAll(list);
                                    LOG.info("历史消息加载成功: sessionId={}, count={}", sessionId, list.size());

                                    // 检查是否有待撤回的乐观消息（刷新后需要补执行撤回）
                                    checkAndExecutePendingRecalls(list);

                                    // 加载成功后上报已读
                                    reportRead();
                                } catch (final Exception e) {
                                    LOG.error("处理历史消息时异常: sessionId={}", sessionId, e);
                                    errorMessage.set("消息渲染异常，请重试");
                                }
                            } else {
                                LOG.info("历史消息列表为 null: sessionId={}", sessionId);
                            }
                        } else {
                            final String msg = response != null ? response.getMessage() : "加载聊天记录失败";
                            errorMessage.set(msg);
                            LOG.warn("聊天记录加载失败: sessionId={}, error={}", sessionId, msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("获取历史消息异常: sessionId={}", sessionId, ex);
                    Platform.runLater(() -> {
                        loading.set(false);
                        errorMessage.set("网络异常，加载历史消息失败");
                    });
                    return null;
                });
    }

    /**
     * 发送消息
     */
    public void sendMessage() {
        final String text = inputText.get();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        if (conversation == null) {
            errorMessage.set("请先选择会话");
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
        optimisticMsg.setCreateTime(java.time.LocalDateTime.now());
        optimisticMsg.setSentByMe(true);

        messages.add(optimisticMsg);
        pendingMessages.put(clientId, optimisticMsg);

        // 通过 WebSocket 发送
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", conversation.getSessionId());
        data.put("msgType", MSG_TYPE_TEXT);
        data.put("content", text.trim());

        if ("AI".equals(conversation.getTargetType())) {
            if (ServerConnectionManager.getInstance().getCurrentMode() == ServerMode.LOCAL) {
                // 本地模式：直接调用 LocalAiEngine，无需 WebSocket
                final Long aiId = conversation.getTargetId();
                final Long userId = currentUser != null ? currentUser.getUserId() : 0L;
                final String sessionId = conversation.getSessionId();
                final String content = text.trim();

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
                            // 更新占位消息为错误提示
                            for (MessageInfo msg : messages) {
                                if (msg.getMessageId() != null && msg.getMessageId() < 0
                                        && "AI".equals(msg.getSenderType())) {
                                    msg.setContent("AI 回复失败: " + error);
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
            } else {
                // 云端/热点模式：使用 WebSocket 转发
                data.put("aiId", conversation.getTargetId());
                WebSocketClient.getInstance().send(MessageTypes.AI_CHAT, data);
            }
        } else {
            WebSocketClient.getInstance().send(MessageTypes.SEND_MESSAGE, data);
        }

        // 清空输入框
        inputText.set("");

        LOG.info("消息已发送: sessionId={}", conversation.getSessionId());
    }

    /**
     * 追加接收到的消息
     *
     * @param message 消息
     */
    public void appendMessage(final MessageInfo message) {
        if (message == null) {
            return;
        }

        // 避免重复添加
        for (final MessageInfo existing : messages) {
            if (existing.getMessageId() != null
                    && existing.getMessageId().equals(message.getMessageId())) {
                return;
            }
        }

        messages.add(message);
    }

    /**
     * 更新消息确认
     *
     * @param clientId   客户端消息ID
     * @param messageId  服务端消息ID
     * @param createTime 创建时间
     */
    public void updateMessageAck(final String clientId, final Long messageId,
            final java.time.LocalDateTime createTime) {
        final MessageInfo pending = pendingMessages.remove(clientId);
        if (pending != null) {
            pending.setMessageId(messageId);
            pending.setCreateTime(createTime);

            // 触发列表更新
            final int index = messages.indexOf(pending);
            if (index >= 0) {
                messages.set(index, pending);
            }

            // 如果该消息注册了延迟撤回，ACK 到达后异步执行 REST 撤回
            if (pendingRecallClientIds.remove(clientId)) {
                LOG.info("ACK 到达，执行异步 REST 撤回: clientId={}, messageId={}", clientId, messageId);
                final String recallContent = pending.getContent();
                ChatService.getInstance().recallMessage(messageId)
                        .thenAccept(response -> {
                            Platform.runLater(() -> {
                                if (response != null && response.isSuccess()) {
                                    // 撤回成功：清理待撤回内容列表，防止 loadHistory 重复执行
                                    if (recallContent != null) {
                                        pendingRecallContents.remove(recallContent);
                                    }
                                    LOG.info("延迟 REST 撤回成功: messageId={}", messageId);
                                } else {
                                    // 撤回失败：不清理 pendingRecallContents，由 checkAndExecutePendingRecalls 重试
                                    pending.setRecalled(false);
                                    final int idx = messages.indexOf(pending);
                                    if (idx >= 0) {
                                        messages.set(idx, pending);
                                    }
                                    final String msg = response != null ? response.getMessage() : "撤回失败";
                                    errorMessage.set(msg);
                                    LOG.warn("延迟 REST 撤回失败（保留在待撤回列表以便重试）: messageId={}, error={}",
                                            messageId, msg);
                                }
                            });
                        })
                        .exceptionally(ex -> {
                            LOG.error("延迟 REST 撤回异常（保留在待撤回列表以便重试）: messageId={}", messageId, ex);
                            Platform.runLater(() -> {
                                pending.setRecalled(false);
                                final int idx = messages.indexOf(pending);
                                if (idx >= 0) {
                                    messages.set(idx, pending);
                                }
                                errorMessage.set("撤回失败，请重试");
                            });
                            return null;
                        });
            }

            LOG.debug("消息确认更新: clientId={}, messageId={}", clientId, messageId);
        }
    }

    /** 是否还有更多历史消息可加载 */
    private boolean hasMoreHistory = true;

    /**
     * 加载更多历史消息
     */
    public void loadMoreHistory() {
        if (conversation == null || loading.get() || !hasMoreHistory) {
            return;
        }

        loading.set(true);
        currentPage++;

        ChatService.getInstance().getHistory(conversation.getSessionId(), currentPage, DEFAULT_PAGE_SIZE)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        loading.set(false);

                        if (response != null && response.isSuccess() && response.getData() != null) {
                            final List<MessageInfo> list = response.getData().getList();
                            if (list != null && !list.isEmpty()) {
                                for (final MessageInfo msg : list) {
                                    msg.setSentByMe(currentUser != null
                                            && currentUser.getUserId() != null
                                            && currentUser.getUserId().equals(msg.getSenderId()));
                                }
                                // 按时间升序排序后插入顶部（先发的在上方）
                                list.sort(java.util.Comparator.comparing(
                                        MessageInfo::getCreateTime,
                                        java.util.Comparator.nullsFirst(java.time.LocalDateTime::compareTo)));
                                messages.addAll(0, list);
                                LOG.info("加载更多消息: count={}", list.size());
                            } else {
                                // 返回空列表说明没有更多历史消息
                                hasMoreHistory = false;
                                LOG.debug("没有更多历史消息");
                            }
                            // 返回数量小于页数也说明没有更多了
                            if (list != null && list.size() < DEFAULT_PAGE_SIZE) {
                                hasMoreHistory = false;
                            }
                        } else {
                            currentPage--;
                            hasMoreHistory = false;
                            LOG.debug("没有更多历史消息");
                        }
                    });
                });
    }

    /**
     * 撤回消息
     * 调用 REST API 撤回，成功后更新消息状态。
     * 如果消息还没被服务端确认（乐观消息，messageId < 0），
     * 则立即标记为已撤回并注册延迟撤回，ACK 到达后自动执行 REST 撤回。
     *
     * @param message 消息对象
     */
    public void recallMessage(final MessageInfo message) {
        if (message == null) {
            return;
        }

        final Long messageId = message.getMessageId();

        // 乐观消息：立即标记撤回，等 ACK 到达后自动执行 REST 撤回
        if (messageId == null || messageId < 0) {
            String clientId = null;
            for (final java.util.Map.Entry<String, MessageInfo> entry : pendingMessages.entrySet()) {
                if (entry.getValue() == message) {
                    clientId = entry.getKey();
                    break;
                }
            }
            if (clientId != null) {
                pendingRecallClientIds.add(clientId);
                // 保存消息内容，用于刷新后匹配执行延迟撤回
                if (message.getContent() != null) {
                    pendingRecallContents.add(message.getContent());
                }
                message.setRecalled(true);
                final int index = messages.indexOf(message);
                if (index >= 0) {
                    messages.set(index, message);
                }
                LOG.info("乐观消息已标记撤回，等待 ACK 后执行 REST 撤回: clientId={}, content={}",
                        clientId, message.getContent());
                return;
            }
            LOG.warn("乐观消息未找到对应 clientId，忽略: messageId={}", messageId);
            return;
        }

        // 已确认消息，直接调 REST 撤回
        ChatService.getInstance().recallMessage(messageId)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.isSuccess()) {
                            message.setRecalled(true);
                            final int index = messages.indexOf(message);
                            if (index >= 0) {
                                messages.set(index, message);
                            }
                            LOG.info("消息撤回成功: messageId={}", messageId);
                        } else {
                            final String msg = response != null ? response.getMessage() : "撤回失败";
                            errorMessage.set(msg);
                            LOG.warn("消息撤回失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("消息撤回异常: messageId={}", messageId, ex);
                    Platform.runLater(() -> errorMessage.set("网络异常，撤回失败"));
                    return null;
                });
    }

    /**
     * 检查并执行待撤回的乐观消息
     * 刷新历史消息后，如果发现有待撤回的消息（内容匹配），立即执行 REST 撤回
     *
     * @param loadedMessages 加载的消息列表
     */
    private void checkAndExecutePendingRecalls(final List<MessageInfo> loadedMessages) {
        if (pendingRecallContents.isEmpty() || loadedMessages == null) {
            return;
        }

        LOG.debug("检查待撤回消息: pendingCount={}, loadedCount={}",
                pendingRecallContents.size(), loadedMessages.size());

        for (final MessageInfo msg : loadedMessages) {
            // 只检查自己发送的消息，且内容匹配待撤回列表
            if (msg.isSentByMe() && msg.getContent() != null
                    && pendingRecallContents.contains(msg.getContent())
                    && msg.getMessageId() != null && msg.getMessageId() > 0) {
                LOG.info("发现待撤回消息，立即执行 REST 撤回: messageId={}, content={}",
                        msg.getMessageId(), msg.getContent());
                // 从待撤回列表中移除（先移除，避免重试风暴）
                pendingRecallContents.remove(msg.getContent());
                // 执行 REST 撤回
                ChatService.getInstance().recallMessage(msg.getMessageId())
                        .thenAccept(response -> {
                            Platform.runLater(() -> {
                                try {
                                    if (response != null && response.isSuccess()) {
                                        msg.setRecalled(true);
                                        final int index = messages.indexOf(msg);
                                        if (index >= 0) {
                                            messages.set(index, msg);
                                        }
                                        LOG.info("延迟 REST 撤回成功（刷新后补执行）: messageId={}",
                                                msg.getMessageId());
                                    } else {
                                        final String errorMsg = response != null ? response.getMessage()
                                                : "撤回失败";
                                        LOG.warn("延迟 REST 撤回失败: messageId={}, error={}",
                                                msg.getMessageId(), errorMsg);
                                        // 撤回失败，重新加入待撤回列表以便用户重试
                                        if (msg.getContent() != null) {
                                            pendingRecallContents.add(msg.getContent());
                                        }
                                    }
                                } catch (final Exception e) {
                                    LOG.error("处理补执行撤回结果时异常: messageId={}", msg.getMessageId(), e);
                                }
                            });
                        })
                        .exceptionally(ex -> {
                            LOG.error("延迟 REST 撤回异常: messageId={}", msg.getMessageId(), ex);
                            return null;
                        });
            }
        }
    }

    /**
     * 上报已读消息
     * 收集所有非自己发送的消息ID，批量上报
     */
    public void reportRead() {
        if (conversation == null || messages.isEmpty()) {
            return;
        }

        final List<Long> unreadMessageIds = new ArrayList<>();
        for (final MessageInfo msg : messages) {
            if (!msg.isSentByMe() && msg.getMessageId() != null && msg.getMessageId() > 0) {
                unreadMessageIds.add(msg.getMessageId());
            }
        }

        if (unreadMessageIds.isEmpty()) {
            LOG.debug("没有需要上报已读的消息");
            return;
        }

        final MarkReadRequest request = new MarkReadRequest(conversation.getSessionId(), unreadMessageIds);
        ChatService.getInstance().markRead(request)
                .thenAccept(response -> {
                    if (response != null && response.isSuccess()) {
                        LOG.info("已读上报成功: sessionId={}, count={}",
                                conversation.getSessionId(), unreadMessageIds.size());
                    } else {
                        LOG.warn("已读上报失败: {}", response != null ? response.getMessage() : "未知错误");
                    }
                });
    }

    /** 图片消息类型常量 */
    private static final String MSG_TYPE_IMAGE = "IMAGE";

    /**
     * 发送图片消息
     * 先上传图片获取URL，再通过WebSocket发送IMAGE类型消息
     *
     * @param filePath 图片文件路径
     */
    public void sendImage(final Path filePath) {
        if (conversation == null) {
            errorMessage.set("未选择会话");
            return;
        }

        loading.set(true);
        ChatService.getInstance().uploadImage(filePath)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        loading.set(false);
                        if (response != null && response.isSuccess() && response.getData() != null) {
                            final ImageUploadResponse uploadResult = response.getData();
                            final String clientId = UUID.randomUUID().toString();

                            // 创建乐观消息
                            final MessageInfo optimisticMsg = new MessageInfo();
                            optimisticMsg.setMessageId(-1L);
                            optimisticMsg.setSessionId(conversation.getSessionId());
                            optimisticMsg.setSenderId(currentUser != null ? currentUser.getUserId() : null);
                            optimisticMsg.setSenderName(currentUser != null ? currentUser.getUsername() : "");
                            optimisticMsg.setSenderType("USER");
                            optimisticMsg.setType(MSG_TYPE_IMAGE);
                            optimisticMsg.setContent(uploadResult.getUrl());
                            // 设置缩略图URL和尺寸信息
                            optimisticMsg.setThumbnailUrl(uploadResult.getThumbnailUrl());
                            optimisticMsg.setWidth(uploadResult.getWidth());
                            optimisticMsg.setHeight(uploadResult.getHeight());
                            optimisticMsg.setCreateTime(LocalDateTime.now());
                            optimisticMsg.setSentByMe(true);

                            // 添加到消息列表并注册待确认
                            messages.add(optimisticMsg);
                            pendingMessages.put(clientId, optimisticMsg);

                            // 通过 WebSocket 发送 IMAGE 类型消息
                            final Map<String, Object> data = new HashMap<>();
                            data.put("sessionId", conversation.getSessionId());
                            data.put("msgType", MSG_TYPE_IMAGE);
                            data.put("content", uploadResult.getUrl());
                            // 添加缩略图和尺寸信息到WebSocket消息
                            data.put("thumbnailUrl", uploadResult.getThumbnailUrl());
                            data.put("width", uploadResult.getWidth());
                            data.put("height", uploadResult.getHeight());

                            WebSocketClient.getInstance().send(MessageTypes.SEND_MESSAGE, data);
                            LOG.info("图片消息发送: url={}, thumbnailUrl={}, clientId={}",
                                    uploadResult.getUrl(), uploadResult.getThumbnailUrl(), clientId);
                        } else {
                            final String msg = response != null ? response.getMessage() : "图片上传失败";
                            errorMessage.set(msg);
                            LOG.warn("图片上传失败: {}", msg);
                        }
                    });
                });
    }

    /** 文件消息类型常量 */
    private static final String MSG_TYPE_FILE = "FILE";

    /**
     * 发送文件消息
     * 通过 WebSocket 发送 FILE 类型消息（包含文件名和大小等元信息）
     *
     * @param filePath 文件路径
     */
    public void sendFile(final Path filePath) {
        if (conversation == null) {
            errorMessage.set("未选择会话");
            return;
        }

        final java.io.File file = filePath.toFile();
        if (!file.exists()) {
            errorMessage.set("文件不存在");
            return;
        }

        final String fileName = file.getName();
        final long fileSize = file.length();
        final String clientId = UUID.randomUUID().toString();

        // 创建乐观消息
        final MessageInfo optimisticMsg = new MessageInfo();
        optimisticMsg.setMessageId(-1L);
        optimisticMsg.setSessionId(conversation.getSessionId());
        optimisticMsg.setSenderId(currentUser != null ? currentUser.getUserId() : null);
        optimisticMsg.setSenderName(currentUser != null ? currentUser.getUsername() : "");
        optimisticMsg.setSenderType("USER");
        optimisticMsg.setType(MSG_TYPE_FILE);
        optimisticMsg.setContent(fileName);
        optimisticMsg.setExtra(String.format("{\"fileSize\": %d, \"filePath\": \"%s\"}",
                fileSize, filePath.toString().replace("\\", "\\\\")));
        optimisticMsg.setCreateTime(LocalDateTime.now());
        optimisticMsg.setSentByMe(true);

        // 添加到消息列表并注册待确认
        messages.add(optimisticMsg);
        pendingMessages.put(clientId, optimisticMsg);

        // 通过 WebSocket 发送 FILE 类型消息
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", conversation.getSessionId());
        data.put("msgType", MSG_TYPE_FILE);
        data.put("content", fileName);
        data.put("fileSize", fileSize);

        WebSocketClient.getInstance().send(MessageTypes.SEND_MESSAGE, data);
        LOG.info("文件消息发送: fileName={}, fileSize={}, clientId={}", fileName, fileSize, clientId);
    }

    /**
     * 获取会话ID
     *
     * @return 会话ID
     */
    public String getSessionId() {
        return conversation != null ? conversation.getSessionId() : null;
    }

    /**
     * 获取会话名称
     *
     * @return 会话名称
     */
    public String getConversationName() {
        return conversation != null ? conversation.getTargetName() : "";
    }

    /**
     * 获取当前会话
     *
     * @return 当前会话信息
     */
    public ConversationInfo getConversation() {
        return conversation;
    }

    // Property getters
    public ListProperty<MessageInfo> messagesProperty() {
        return messages;
    }

    public ObservableList<MessageInfo> getMessages() {
        return messages.get();
    }

    public StringProperty inputTextProperty() {
        return inputText;
    }

    public String getInputText() {
        return inputText.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /**
     * 向指定会话添加待注入的系统消息（群成员加入等）
     *
     * @param sessionId 会话ID
     * @param message   系统消息
     */
    public static void addPendingSystemMessage(final String sessionId, final MessageInfo message) {
        pendingSystemMessages.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    /**
     * 取出并注入所有待处理的系统消息到当前消息列表
     *
     * @param sessionId 会话ID
     */
    private void drainPendingSystemMessages(final String sessionId) {
        final List<MessageInfo> pending = pendingSystemMessages.remove(sessionId);
        if (pending != null && !pending.isEmpty()) {
            messages.addAll(pending);
            LOG.info("注入系统消息: sessionId={}, count={}", sessionId, pending.size());
        }
    }

    /** AI 流式输出缓存：streamMessageId -> MessageInfo（用于增量追加），实例变量避免跨会话污染 */
    private final Map<String, MessageInfo> aiStreamCache = new ConcurrentHashMap<>();

    /**
     * 处理 AI 流式输出
     * 首次收到时创建 AI 消息占位，后续追加内容，完成时设置最终 messageId
     *
     * @param streamMessageId 流式消息ID（客户端发送时的ID）
     * @param content         增量内容（done=false）或完整内容（done=true）
     * @param done            是否完成
     * @param aiMessageId     AI 消息的服务端ID（仅 done=true 时有值）
     */
    public void handleAiStream(final String streamMessageId, final String content,
            final boolean done, final Long aiMessageId) {
        if (content == null) {
            return;
        }

        MessageInfo aiMsg = aiStreamCache.get(streamMessageId);

        if (aiMsg == null) {
            if (done) {
                LOG.warn("[AI-STREAM] 收到完成消息但无占位缓存，可能是超时清理或异常重发: streamId={}", streamMessageId);
                return;
            }
            // 首次收到流式消息，创建 AI 消息占位
            aiMsg = new MessageInfo();
            aiMsg.setMessageId(-System.currentTimeMillis());
            aiMsg.setSessionId(conversation.getSessionId());
            aiMsg.setSenderId(conversation.getTargetId());
            aiMsg.setSenderName(conversation.getTargetName());
            aiMsg.setSenderType("AI");
            aiMsg.setType("TEXT");
            aiMsg.setContent(content);
            aiMsg.setCreateTime(java.time.LocalDateTime.now());
            aiMsg.setSentByMe(false);

            messages.add(aiMsg);
            aiStreamCache.put(streamMessageId, aiMsg);
            LOG.debug("[AI-STREAM] 创建AI消息占位: streamId={}", streamMessageId);
        } else {
            // 增量追加内容
            aiMsg.setContent(aiMsg.getContent() + content);
            // 触发列表更新
            final int index = messages.indexOf(aiMsg);
            if (index >= 0) {
                messages.set(index, aiMsg);
            }
        }

        if (done) {
            // 流式输出完成
            if (aiMessageId != null) {
                aiMsg.setMessageId(aiMessageId);
            }
            aiStreamCache.remove(streamMessageId);
            // 最终刷新一次列表
            final int index = messages.indexOf(aiMsg);
            if (index >= 0) {
                messages.set(index, aiMsg);
            }
            LOG.info("[AI-STREAM] AI流式输出完成: streamId={}, aiMessageId={}", streamMessageId, aiMessageId);
        }
    }
}
