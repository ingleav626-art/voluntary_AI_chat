package org.example.client.view;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

        // AI 会话从本地引擎加载，真人会话从服务端加载
        if (sessionId != null && sessionId.startsWith("a_")) {
            loadAiConversationHistory(sessionId);
            return;
        }

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

                                    // 群聊：补充本地AI消息
                                    if (sessionId != null && sessionId.startsWith("g_")) {
                                        loadGroupAiMessages(sessionId);
                                    }

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

        // 使用 MessageSender 发送消息（拆分逻辑，避免文件超限）
        org.example.client.service.MessageSender.getInstance().sendMessage(
                currentUser, conversation, text, messages, pendingMessages);

        // 清空输入框
        inputText.set("");
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
            // 在修改 messageId 前获取 index，因为 Lombok @Data 的 equals()
            // 依赖 messageId 字段，修改后 indexOf 会返回 -1
            final int index = messages.indexOf(pending);

            pending.setMessageId(messageId);
            pending.setCreateTime(createTime);

            // currentMsg：messages 列表中当前实际使用的引用（可能是新对象以触发重绘）
            final MessageInfo currentMsg;
            if (index >= 0) {
                // 创建新 MessageInfo 对象（不同引用），用 remove+add 替代 set，
                // 强制 JavaFX ListView 调用 updateItem 重绘单元格，使右键菜单
                // 能立即从"发送中..."更新为"撤回"
                final MessageInfo confirmed = new MessageInfo();
                confirmed.setMessageId(messageId);
                confirmed.setCreateTime(createTime);
                confirmed.setSessionId(pending.getSessionId());
                confirmed.setSenderId(pending.getSenderId());
                confirmed.setSenderName(pending.getSenderName());
                confirmed.setSenderAvatar(pending.getSenderAvatar());
                confirmed.setSenderType(pending.getSenderType());
                confirmed.setType(pending.getType());
                confirmed.setContent(pending.getContent());
                confirmed.setThumbnailUrl(pending.getThumbnailUrl());
                confirmed.setWidth(pending.getWidth());
                confirmed.setHeight(pending.getHeight());
                confirmed.setRecalled(pending.isRecalled());
                confirmed.setExtra(pending.getExtra());
                confirmed.setRead(pending.isRead());
                confirmed.setSentByMe(pending.isSentByMe());
                messages.remove(index);
                messages.add(index, confirmed);
                currentMsg = confirmed;
            } else {
                currentMsg = pending;
            }

            // 如果该消息注册了延迟撤回，ACK 到达后异步执行 REST 撤回
            if (pendingRecallClientIds.remove(clientId)) {
                LOG.info("ACK 到达，执行异步 REST 撤回: clientId={}, messageId={}", clientId, messageId);
                final String recallContent = currentMsg.getContent();
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
                                    currentMsg.setRecalled(false);
                                    final int idx = messages.indexOf(currentMsg);
                                    if (idx >= 0) {
                                        messages.set(idx, currentMsg);
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
                                currentMsg.setRecalled(false);
                                final int idx = messages.indexOf(currentMsg);
                                if (idx >= 0) {
                                    messages.set(idx, currentMsg);
                                }
                                errorMessage.set("撤回失败，请重试");
                            });
                            return null;
                        });
            }

            // ACK 到达后自动刷新消息列表，使右键菜单从"发送中..."更新为"撤回"
            Platform.runLater(() -> {
                messages.setAll(new ArrayList<>(messages));
            });
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

        Long messageId = message.getMessageId();

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

            // 未找到 clientId：ACK 可能已并发到达并更新了 messageId
            // 重新读取 messageId（ACK 线程可能已经将其更新为正数）
            messageId = message.getMessageId();
            if (messageId == null || messageId < 0) {
                LOG.warn("乐观消息未找到对应 clientId，忽略: messageId={}", messageId);
                return;
            }
            // ACK 已到达，messageId 已被更新，继续执行已确认路径的 REST 撤回
            LOG.info("乐观消息已由 ACK 并发确认，直接执行 REST 撤回: messageId={}", messageId);
        }

        // 已确认消息，直接调 REST 撤回
        final Long finalMessageId = messageId;
        ChatService.getInstance().recallMessage(finalMessageId)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.isSuccess()) {
                            message.setRecalled(true);
                            final int index = messages.indexOf(message);
                            if (index >= 0) {
                                messages.set(index, message);
                            }
                            LOG.info("消息撤回成功: messageId={}", finalMessageId);
                        } else {
                            final String msg = response != null ? response.getMessage() : "撤回失败";
                            errorMessage.set(msg);
                            LOG.warn("消息撤回失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("消息撤回异常: messageId={}", finalMessageId, ex);
                    Platform.runLater(() -> errorMessage.set("网络异常，撤回失败"));
                    return null;
                });
    }

    /** 撤回超时错误码（code=4001），此错误不可恢复 */
    private static final int RECALL_TIMEOUT_CODE = 4001;

    /**
     * 检查并执行待撤回的乐观消息
     * 刷新历史消息后，如果发现有待撤回的消息（内容匹配），立即执行 REST 撤回
     * <p>
     * <b>注意</b>：仅匹配"自己发送 + 内容相同 + 未撤回 + 2分钟内"的消息，
     * 避免因内容重复（如"1"）误匹配到其他消息。
     * </p>
     *
     * @param loadedMessages 加载的消息列表
     */
    private void checkAndExecutePendingRecalls(final List<MessageInfo> loadedMessages) {
        if (pendingRecallContents.isEmpty() || loadedMessages == null) {
            return;
        }

        LOG.debug("检查待撤回消息: pendingCount={}, loadedCount={}",
                pendingRecallContents.size(), loadedMessages.size());

        final java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(2);

        for (final MessageInfo msg : loadedMessages) {
            // 只检查自己发送的消息，且内容匹配待撤回列表
            if (msg.isSentByMe() && msg.getContent() != null
                    && pendingRecallContents.contains(msg.getContent())
                    && msg.getMessageId() != null && msg.getMessageId() > 0) {

                // 关键修复：先检查服务器是否已经撤回了该消息
                if (msg.isRecalled()) {
                    // 服务器已经撤回，跳过但不移除 pendingRecallContents
                    // （可能有其他同内容消息仍需撤回，如多个"1"）
                    LOG.info("服务器已撤回该消息，跳过: messageId={}, content={}",
                            msg.getMessageId(), msg.getContent());
                    continue; // 跳过，继续检查其他同内容消息
                }

                // 检查消息是否超过2分钟撤回窗口
                if (msg.getCreateTime() != null && msg.getCreateTime().isBefore(cutoff)) {
                    // 超过2分钟，永久不可撤回，跳过但不移除 pendingRecallContents
                    // （可能有其他同内容消息仍在2分钟窗口内）
                    LOG.info("消息已超过2分钟撤回窗口，跳过: messageId={}, content={}",
                            msg.getMessageId(), msg.getContent());
                    continue; // 跳过，继续检查其他同内容消息
                }

                // 消息尚未撤回且未超过2分钟，执行 REST 撤回
                LOG.info("发现待撤回消息，立即执行 REST 撤回: messageId={}, content={}",
                        msg.getMessageId(), msg.getContent());
                // 从待撤回列表中移除（先移除，避免重试风暴）
                final String recallContent = msg.getContent();
                pendingRecallContents.remove(recallContent);
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
                                        final int code = response != null ? response.getCode() : 0;
                                        LOG.warn("延迟 REST 撤回失败: messageId={}, code={}, error={}",
                                                msg.getMessageId(), code, errorMsg);
                                        // 仅临时性错误才重新加入待撤回列表
                                        // 永久性错误（如超时code=4001）不再重试
                                        if (code != RECALL_TIMEOUT_CODE && recallContent != null) {
                                            pendingRecallContents.add(recallContent);
                                            LOG.info("临时性撤回失败，重新加入待撤回列表以便重试: content={}", recallContent);
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

    /**
     * 加载 AI 会话的历史消息（从本地引擎）
     *
     * @param sessionId AI会话ID（格式: a_{aiId}）
     */
    private void loadAiConversationHistory(final String sessionId) {
        try {
            // 构建正确的sessionId格式：a_{aiId}_{userId}
            final Long userId = currentUser.getUserId();
            final String fullSessionId = sessionId + "_" + userId;
            LOG.info("构建完整AI会话ID: original={}, full={}", sessionId, fullSessionId);

            final List<com.voluntary.chat.server.entity.Message> aiMessages =
                    org.example.client.engine.LocalAiEngine.getInstance()
                            .getAiConversationHistory(fullSessionId, DEFAULT_PAGE_SIZE * 5);

            Platform.runLater(() -> {
                loading.set(false);

                final List<MessageInfo> list = new java.util.ArrayList<>();
                for (final com.voluntary.chat.server.entity.Message msg : aiMessages) {
                    final MessageInfo info = new MessageInfo();
                    info.setMessageId(msg.getId());
                    info.setSessionId(msg.getSessionId());
                    info.setSenderId(msg.getSenderId());
                    info.setSenderType(msg.getSenderType() == 1 ? "AI" : "USER");
                    info.setType(msg.getType() == 0 ? "TEXT" : "IMAGE");
                    info.setContent(msg.getContent());
                    info.setCreateTime(msg.getCreateTime());
                    info.setRecalled(msg.getRecallTime() != null);
                    info.setSentByMe(currentUser != null
                            && currentUser.getUserId() != null
                            && currentUser.getUserId().equals(msg.getSenderId()));
                    info.setRead(true); // AI消息默认已读
                    list.add(info);
                }

                // 按时间升序排序
                list.sort(java.util.Comparator.comparing(
                        MessageInfo::getCreateTime,
                        java.util.Comparator.nullsFirst(java.time.LocalDateTime::compareTo)));

                messages.setAll(list);
                LOG.info("AI会话历史消息加载成功: sessionId={}, count={}", sessionId, list.size());
            });
        } catch (final Exception e) {
            LOG.error("加载AI会话历史消息失败: sessionId={}", sessionId, e);
            Platform.runLater(() -> {
                loading.set(false);
                errorMessage.set("加载AI聊天记录失败: " + e.getMessage());
            });
        }
    }

    /** AI 流式输出缓存：streamMessageId -> MessageInfo（用于增量追加），实例变量避免跨会话污染 */
    private final Map<String, MessageInfo> aiStreamCache = new ConcurrentHashMap<>();

    /**
     * 加载群聊中的本地AI消息并合并到消息列表
     *
     * @param sessionId 群聊会话ID（格式: g_{groupId}）
     */
    private void loadGroupAiMessages(final String sessionId) {
        try {
            LOG.info("开始加载群聊本地AI消息: sessionId={}", sessionId);
            final String[] parts = sessionId.split("_");
            final Long groupId = Long.parseLong(parts[1]);

            final List<com.voluntary.chat.server.entity.Message> aiMessages =
                    org.example.client.engine.LocalAiEngine.getInstance()
                            .getGroupAiMessages(groupId, DEFAULT_PAGE_SIZE * 10);

            LOG.info("群聊本地AI消息查询结果: sessionId={}, 查得数量={}", sessionId,
                    aiMessages != null ? aiMessages.size() : 0);

            if (aiMessages == null || aiMessages.isEmpty()) {
                LOG.info("群聊本地AI消息为空，无需补充: sessionId={}", sessionId);
                return;
            }

            // 去重合并：按messageId检查，不重复的添加到messages列表
            final java.util.Set<Long> existingIds = new java.util.HashSet<>();
            for (final MessageInfo msg : messages) {
                if (msg.getMessageId() != null) {
                    existingIds.add(msg.getMessageId());
                }
            }
            LOG.info("群聊本地AI消息去重: sessionId={}, 已有消息数={}", sessionId, existingIds.size());

            int addedCount = 0;
            for (final com.voluntary.chat.server.entity.Message msg : aiMessages) {
                if (existingIds.contains(msg.getId())) {
                    LOG.debug("跳过重复消息: messageId={}", msg.getId());
                    continue;
                }

                final MessageInfo info = new MessageInfo();
                info.setMessageId(msg.getId());
                info.setSessionId(msg.getSessionId());
                info.setSenderId(msg.getSenderId());
                info.setSenderType(msg.getSenderType() == 1 ? "AI" : "USER");
                info.setContent(msg.getContent());
                info.setCreateTime(msg.getCreateTime());
                info.setType(msg.getType() == 0 ? "TEXT" : "IMAGE");
                info.setRecalled(msg.getRecallTime() != null);
                info.setSentByMe(currentUser != null
                        && currentUser.getUserId() != null
                        && currentUser.getUserId().equals(msg.getSenderId()));
                info.setRead(true);
                // 根据发送者类型设置发送者名称
                if (msg.getSenderType() == 1) {
                    // AI 消息 - 从本地引擎获取AI名称
                    final com.voluntary.chat.server.entity.AiProfile aiProfile =
                            org.example.client.engine.LocalAiEngine.getInstance().getAiProfile(msg.getSenderId());
                    info.setSenderName(aiProfile != null ? aiProfile.getName() : "AI");
                } else {
                    // 用户消息 - 如果是当前用户则显示当前用户名，否则显示"用户"
                    if (currentUser != null && currentUser.getUserId() != null
                            && currentUser.getUserId().equals(msg.getSenderId())) {
                        info.setSenderName(currentUser.getUsername());
                    } else {
                        info.setSenderName("用户" + msg.getSenderId());
                    }
                }
                messages.add(info);
                existingIds.add(msg.getId());
                addedCount++;
                LOG.debug("添加群聊本地消息: messageId={}, senderType={}, content={}",
                        msg.getId(), msg.getSenderType(),
                        msg.getContent() != null ? msg.getContent().substring(0, Math.min(50, msg.getContent().length())) : "");
            }

            if (addedCount > 0) {
                messages.sort(java.util.Comparator.comparing(
                        MessageInfo::getCreateTime,
                        java.util.Comparator.nullsFirst(java.time.LocalDateTime::compareTo)));
                LOG.info("群聊本地AI消息补充完成: sessionId={}, 补充数量={}, 总消息数={}",
                        sessionId, addedCount, messages.size());
            } else {
                LOG.info("群聊本地AI消息无需补充: sessionId={}, 所有消息已存在", sessionId);
            }
        } catch (final Exception e) {
            LOG.error("加载群聊本地AI消息失败: sessionId={}", sessionId, e);
        }
    }

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

    /**
     * 处理群聊 AI 流式输出
     *
     * @param streamMessageId 流式消息ID
     * @param content         内容
     * @param done            是否完成
     * @param aiMessageId     AI消息ID
     * @param senderId        发送者ID
     * @param senderName      发送者名称
     * @param senderAvatar    发送者头像
     * @param senderType      发送者类型
     */
    public void handleGroupAiStream(final String streamMessageId, final String content,
            final boolean done, final Long aiMessageId,
            final Long senderId, final String senderName,
            final String senderAvatar, final String senderType) {
        if (content == null) {
            return;
        }

        MessageInfo aiMsg = aiStreamCache.get(streamMessageId);

        if (aiMsg == null) {
            if (done) {
                LOG.warn("[AI-STREAM] 群聊收到完成消息但无占位缓存: streamId={}", streamMessageId);
                return;
            }
            // 首次收到流式消息，创建 AI 消息占位
            aiMsg = new MessageInfo();
            aiMsg.setMessageId(-System.currentTimeMillis());
            aiMsg.setSessionId(conversation.getSessionId());
            aiMsg.setSenderId(senderId);
            aiMsg.setSenderName(senderName != null ? senderName : "AI");
            aiMsg.setSenderAvatar(senderAvatar);
            aiMsg.setSenderType(senderType != null ? senderType : "AI");
            aiMsg.setType("TEXT");
            aiMsg.setContent(content);
            aiMsg.setCreateTime(java.time.LocalDateTime.now());
            aiMsg.setSentByMe(false);

            messages.add(aiMsg);
            aiStreamCache.put(streamMessageId, aiMsg);
            LOG.debug("[AI-STREAM] 群聊创建AI消息占位: streamId={}, sender={}", streamMessageId, senderName);
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
            LOG.info("[AI-STREAM] 群聊AI流式输出完成: streamId={}, aiMessageId={}", streamMessageId, aiMessageId);
        }
    }
}
