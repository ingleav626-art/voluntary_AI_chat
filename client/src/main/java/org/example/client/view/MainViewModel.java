package org.example.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.example.client.model.ConversationInfo;
import org.example.client.model.LoginResponse;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.example.client.service.ChatService;
import org.example.client.service.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 主界面视图模型（MVVM）
 *
 * <p>管理当前用户信息、会话列表、WebSocket 连接和消息分发。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class MainViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(MainViewModel.class);

    /** 当前用户信息 */
    private final ObjectProperty<UserInfo> currentUser = new SimpleObjectProperty<>();

    /** 会话列表 */
    private final ListProperty<ConversationInfo> conversations =
            new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 当前选中的会话 */
    private final ObjectProperty<ConversationInfo> selectedConversation = new SimpleObjectProperty<>();

    /** WebSocket 连接状态 */
    private final BooleanProperty connected = new SimpleBooleanProperty(false);

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 当前聊天视图模型 */
    private final ObjectProperty<ChatViewModel> chatViewModel = new SimpleObjectProperty<>();

    /** 退出登录回调 */
    private Consumer<Void> onLogout;

    public MainViewModel() {
        // 初始化 WebSocket 消息回调
        WebSocketClient.getInstance().setOnMessage(this::handleWebSocketMessage);
        WebSocketClient.getInstance().setOnConnectionChange(this::handleConnectionChange);
    }

    /**
     * 初始化主界面
     *
     * @param loginResponse 登录响应
     */
    public void initialize(final LoginResponse loginResponse) {
        if (loginResponse == null || loginResponse.getUser() == null) {
            LOG.error("登录响应为空，无法初始化主界面");
            errorMessage.set("登录信息异常");
            return;
        }

        // 设置当前用户
        currentUser.set(loginResponse.getUser());

        // 建立 WebSocket 连接
        WebSocketClient.getInstance().connect(loginResponse.getAccessToken());

        // 加载会话列表
        loadConversations();

        LOG.info("主界面初始化完成: userId={}", loginResponse.getUser().getUserId());
    }

    /**
     * 加载会话列表
     */
    public void loadConversations() {
        loading.set(true);
        errorMessage.set("");

        ChatService.getInstance().getConversations()
                .thenAccept(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        final List<ConversationInfo> list = response.getData() != null
                                ? response.getData().getList() : new ArrayList<>();
                        conversations.setAll(list);
                        LOG.info("会话列表加载成功: count={}", list.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载会话列表失败";
                        errorMessage.set(msg);
                        LOG.warn("会话列表加载失败: {}", msg);
                    }
                });
    }

    /**
     * 选择会话
     *
     * @param conversation 会话
     */
    public void selectConversation(final ConversationInfo conversation) {
        selectedConversation.set(conversation);

        if (conversation != null) {
            // 创建聊天视图模型并加载历史消息
            final ChatViewModel chatVm = new ChatViewModel(
                    currentUser.get(), conversation);
            chatViewModel.set(chatVm);
            chatVm.loadHistory();

            LOG.info("选中会话: sessionId={}, name={}",
                    conversation.getSessionId(), conversation.getTargetName());
        } else {
            chatViewModel.set(null);
        }
    }

    /**
     * 处理 WebSocket 消息
     *
     * @param wsMessage WebSocket 消息
     */
    private void handleWebSocketMessage(final WebSocketMessage wsMessage) {
        if (wsMessage == null || wsMessage.getType() == null) {
            return;
        }

        switch (wsMessage.getType()) {
            case MessageTypes.RECEIVE_MESSAGE -> handleReceiveMessage(wsMessage);
            case MessageTypes.MESSAGE_ACK -> handleAck(wsMessage);
            case MessageTypes.STATUS_CHANGE -> handleStatusChange(wsMessage);
            default -> LOG.debug("未处理的消息类型: {}", wsMessage.getType());
        }
    }

    /**
     * 处理接收消息
     *
     * @param wsMessage WebSocket 消息
     */
    @SuppressWarnings("unchecked")
    private void handleReceiveMessage(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }

        final String sessionId = (String) data.get("sessionId");
        final Long messageId = toLong(data.get("messageId"));
        final Long senderId = toLong(data.get("senderId"));
        final String senderName = (String) data.get("senderName");
        final String senderAvatar = (String) data.get("senderAvatar");
        final String senderType = (String) data.get("senderType");
        final String msgType = (String) data.get("msgType");
        final String content = (String) data.get("content");
        final String createTimeStr = (String) data.get("createTime");

        final MessageInfo message = new MessageInfo();
        message.setMessageId(messageId);
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setSenderAvatar(senderAvatar);
        message.setSenderType(senderType);
        message.setType(msgType);
        message.setContent(content);
        message.setCreateTime(parseDateTime(createTimeStr));
        message.setSentByMe(currentUser.get() != null
                && currentUser.get().getUserId() != null
                && currentUser.get().getUserId().equals(senderId));

        // 更新会话最后消息
        updateConversationLastMessage(sessionId, content);

        // 如果当前正在查看该会话，追加消息到聊天区
        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm != null && sessionId.equals(chatVm.getSessionId())) {
            chatVm.appendMessage(message);
        }

        LOG.info("收到消息: sessionId={}, messageId={}", sessionId, messageId);
    }

    /**
     * 处理消息确认
     *
     * @param wsMessage WebSocket 消息
     */
    @SuppressWarnings("unchecked")
    private void handleAck(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }

        final String clientId = (String) data.get("clientId");
        final Long messageId = toLong(data.get("messageId"));
        final String createTimeStr = (String) data.get("createTime");

        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm != null) {
            chatVm.updateMessageAck(clientId, messageId, parseDateTime(createTimeStr));
        }

        LOG.debug("消息确认: clientId={}, messageId={}", clientId, messageId);
    }

    /**
     * 处理在线状态变更
     *
     * @param wsMessage WebSocket 消息
     */
    @SuppressWarnings("unchecked")
    private void handleStatusChange(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }

        final Long userId = toLong(data.get("userId"));
        final Boolean online = (Boolean) data.get("online");

        LOG.info("用户在线状态变更: userId={}, online={}", userId, online);
        // 后续可更新会话列表中的在线状态
    }

    /**
     * 处理连接状态变更
     *
     * @param isConnected 是否已连接
     */
    private void handleConnectionChange(final boolean isConnected) {
        connected.set(isConnected);
        if (!isConnected) {
            LOG.warn("WebSocket 连接断开");
        }
    }

    /**
     * 更新会话最后消息
     *
     * @param sessionId 会话ID
     * @param content 消息内容
     */
    private void updateConversationLastMessage(final String sessionId, final String content) {
        for (final ConversationInfo conv : conversations) {
            if (sessionId.equals(conv.getSessionId())) {
                conv.setLastMessage(content);
                // 触发列表更新
                final ConversationInfo updated = conv;
                final int index = conversations.indexOf(updated);
                if (index >= 0) {
                    conversations.set(index, updated);
                }
                break;
            }
        }
    }

    /**
     * 退出登录
     */
    public void logout() {
        WebSocketClient.getInstance().close();
        currentUser.set(null);
        conversations.clear();
        selectedConversation.set(null);
        chatViewModel.set(null);

        if (onLogout != null) {
            onLogout.accept(null);
        }

        LOG.info("已退出登录");
    }

    /**
     * 对象转 Long
     *
     * @param value 值
     * @return Long
     */
    private Long toLong(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析时间字符串
     *
     * @param timeStr 时间字符串
     * @return LocalDateTime
     */
    private java.time.LocalDateTime parseDateTime(final String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(timeStr.replace("Z", ""));
        } catch (final Exception e) {
            LOG.warn("时间解析失败: {}", timeStr);
            return null;
        }
    }

    // Property getters
    public ObjectProperty<UserInfo> currentUserProperty() {
        return currentUser;
    }

    public ListProperty<ConversationInfo> conversationsProperty() {
        return conversations;
    }

    public ObservableList<ConversationInfo> getConversations() {
        return conversations.get();
    }

    public ObjectProperty<ConversationInfo> selectedConversationProperty() {
        return selectedConversation;
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public ObjectProperty<ChatViewModel> chatViewModelProperty() {
        return chatViewModel;
    }

    public ChatViewModel getChatViewModel() {
        return chatViewModel.get();
    }

    public UserInfo getCurrentUser() {
        return currentUser.get();
    }

    public void setOnLogout(final Consumer<Void> callback) {
        this.onLogout = callback;
    }
}
