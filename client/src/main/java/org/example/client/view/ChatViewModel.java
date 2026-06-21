package org.example.client.view;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * <p>管理当前会话的消息列表、消息发送和历史记录加载。</p>
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
    private final ListProperty<MessageInfo> messages =
            new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 输入框文本 */
    private final StringProperty inputText = new SimpleStringProperty("");

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 待确认消息（clientId -> MessageInfo） */
    private final Map<String, MessageInfo> pendingMessages = new HashMap<>();

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
            return;
        }

        loading.set(true);
        currentPage = 1;

        ChatService.getInstance().getHistory(conversation.getSessionId(), currentPage, DEFAULT_PAGE_SIZE)
                .thenAccept(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final List<MessageInfo> list = response.getData().getList();
                        if (list != null) {
                            // 标记是否为当前用户发送
                            for (final MessageInfo msg : list) {
                                msg.setSentByMe(currentUser != null
                                        && currentUser.getUserId() != null
                                        && currentUser.getUserId().equals(msg.getSenderId()));
                            }
                            // 按时间升序排序（先发的在上方），服务端默认按时间倒序返回
                            list.sort(java.util.Comparator.comparing(
                                    MessageInfo::getCreateTime,
                                    java.util.Comparator.nullsFirst(java.time.LocalDateTime::compareTo)));
                            messages.setAll(list);
                            LOG.info("历史消息加载成功: count={}", list.size());

                            // 加载成功后上报已读
                            reportRead();
                        }
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载聊天记录失败";
                        errorMessage.set(msg);
                        LOG.warn("聊天记录加载失败: {}", msg);
                    }
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

        WebSocketClient.getInstance().send(MessageTypes.SEND_MESSAGE, data);

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
     * @param clientId 客户端消息ID
     * @param messageId 服务端消息ID
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

            LOG.debug("消息确认更新: clientId={}, messageId={}", clientId, messageId);
        }
    }

    /**
     * 加载更多历史消息
     */
    public void loadMoreHistory() {
        if (conversation == null || loading.get()) {
            return;
        }

        loading.set(true);
        currentPage++;

        ChatService.getInstance().getHistory(conversation.getSessionId(), currentPage, DEFAULT_PAGE_SIZE)
                .thenAccept(response -> {
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
                        }
                    } else {
                        currentPage--;
                        LOG.debug("没有更多历史消息");
                    }
                });
    }

    /**
     * 撤回消息
     * 调用 REST API 撤回，成功后更新消息状态
     *
     * @param messageId 消息ID
     */
    public void recallMessage(final Long messageId) {
        if (messageId == null || messageId < 0) {
            errorMessage.set("消息ID无效，无法撤回");
            return;
        }

        ChatService.getInstance().recallMessage(messageId)
                .thenAccept(response -> {
                    if (response != null && response.isSuccess()) {
                        // 更新消息状态为已撤回
                        for (final MessageInfo msg : messages) {
                            if (messageId.equals(msg.getMessageId())) {
                                msg.setRecalled(true);
                                final int index = messages.indexOf(msg);
                                if (index >= 0) {
                                    messages.set(index, msg);
                                }
                                break;
                            }
                        }
                        LOG.info("消息撤回成功: messageId={}", messageId);
                    } else {
                        final String msg = response != null ? response.getMessage() : "撤回失败";
                        errorMessage.set(msg);
                        LOG.warn("消息撤回失败: {}", msg);
                    }
                });
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
                    loading.set(false);
                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final ImageUploadResponse uploadResult = response.getData();
                        // 通过 WebSocket 发送 IMAGE 类型消息
                        final String clientId = UUID.randomUUID().toString();
                        final Map<String, Object> data = new HashMap<>();
                        data.put("sessionId", conversation.getSessionId());
                        data.put("msgType", MSG_TYPE_IMAGE);
                        data.put("content", uploadResult.getUrl());

                        WebSocketClient.getInstance().send(MessageTypes.SEND_MESSAGE, data);
                        LOG.info("图片消息发送: url={}", uploadResult.getUrl());
                    } else {
                        final String msg = response != null ? response.getMessage() : "图片上传失败";
                        errorMessage.set(msg);
                        LOG.warn("图片上传失败: {}", msg);
                    }
                });
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
}
