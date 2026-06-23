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
 * <p>
 * 管理当前用户信息、会话列表、WebSocket 连接和消息分发。
 * </p>
 *
 * <p>
 * <b>TODO:⚠️ 类长度超限警告：当前526行，超出Service限制（400行）</b>
 * <br>请勿在此类中添加新的职责，应拆分为：
 * <ul>
 * <li>WebSocketMessageHandler（WebSocket消息处理）</li>
 * <li>ConversationManager（会话列表管理）</li>
 * <li>MainViewModel（主视图模型，协调上述组件）</li>
 * </ul>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class MainViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(MainViewModel.class);

    /** 当前用户信息 */
    private final ObjectProperty<UserInfo> currentUser = new SimpleObjectProperty<>();

    /** 会话列表（显示用，可能被搜索过滤） */
    private final ListProperty<ConversationInfo> conversations = new SimpleListProperty<>(
            FXCollections.observableArrayList());

    /** 全部会话列表（搜索过滤的源数据） */
    private final ObservableList<ConversationInfo> allConversations = FXCollections.observableArrayList();

    /** 当前选中的会话 */
    private final ObjectProperty<ConversationInfo> selectedConversation = new SimpleObjectProperty<>();

    /** 搜索关键词 */
    private final StringProperty searchKeyword = new SimpleStringProperty("");

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

        // 建立 WebSocket 连接（仅在未连接时）
        if (!WebSocketClient.getInstance().isConnected()) {
            WebSocketClient.getInstance().connect(loginResponse.getAccessToken());
        } else {
            LOG.info("WebSocket 已连接，跳过重新建立连接");
            // 同步连接状态到当前 ViewModel
            connected.set(true);
        }

        // 加载会话列表
        loadConversations();

        LOG.info("主界面初始化完成: userId={}", loginResponse.getUser().getUserId());
    }

    /**
     * 加载会话列表
     */
    public void loadConversations() {
        loadConversations(searchKeyword.get());
    }

    /**
     * 按关键词搜索会话（本地过滤，不请求服务端）
     */
    public void searchConversations(final String keyword) {
        searchKeyword.set(keyword != null ? keyword : "");
        filterConversations(keyword);
    }

    /**
     * 加载会话列表（从服务端获取全部数据）
     */
    private void fetchConversationsFromServer() {
        loading.set(true);
        errorMessage.set("");

        ChatService.getInstance().getConversations()
                .thenAccept(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        final List<ConversationInfo> list = response.getData() != null
                                ? response.getData().getList()
                                : new ArrayList<>();
                        allConversations.setAll(list);
                        // 如果有搜索关键词，应用过滤；否则显示全部
                        final String kw = searchKeyword.get();
                        if (kw != null && !kw.trim().isEmpty()) {
                            filterConversations(kw);
                        } else {
                            conversations.setAll(list);
                        }
                        LOG.info("会话列表加载成功: count={}", list.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载会话列表失败";
                        errorMessage.set(msg);
                        LOG.warn("会话列表加载失败: {}", msg);
                    }
                });
    }

    /**
     * 加载会话列表（带搜索关键词）- 已废弃，改用本地过滤
     *
     * @param keyword 搜索关键词，为空则返回全部
     * @deprecated 使用 {@link #loadConversations()} + {@link #filterConversations(String)} 替代
     */
    @Deprecated
    private void loadConversations(final String keyword) {
        fetchConversationsFromServer();
    }

    /**
     * 选择会话
     * 选中后清零未读数，加载历史消息并上报已读
     *
     * @param conversation 会话
     */
    public void selectConversation(final ConversationInfo conversation) {
        selectedConversation.set(conversation);

        if (conversation != null) {
            // 清零未读数
            if (conversation.getUnreadCount() > 0) {
                conversation.setUnreadCount(0);
                final int index = conversations.indexOf(conversation);
                if (index >= 0) {
                    conversations.set(index, conversation);
                }
            }

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
            case MessageTypes.GROUP_MESSAGE -> handleGroupMessage(wsMessage);
            case MessageTypes.MESSAGE_ACK -> handleAck(wsMessage);
            case MessageTypes.STATUS_CHANGE -> handleStatusChange(wsMessage);
            case MessageTypes.RECONNECT_ACK -> handleReconnectAck(wsMessage);
            case MessageTypes.READ_RECEIPT -> handleReadReceipt(wsMessage);
            case MessageTypes.MESSAGE_RECALL -> handleMessageRecall(wsMessage);
            // 群组事件通知
            case MessageTypes.GROUP_MEMBER_JOIN -> handleGroupMemberJoin(wsMessage);
            case MessageTypes.GROUP_MEMBER_LEAVE -> handleGroupMemberLeave(wsMessage);
            case MessageTypes.GROUP_MEMBER_ROLE_CHANGE -> handleGroupMemberRoleChange(wsMessage);
            case MessageTypes.GROUP_INFO_CHANGE -> handleGroupInfoChange(wsMessage);
            case MessageTypes.GROUP_DISMISSED -> handleGroupDismissed(wsMessage);
            default -> LOG.debug("未处理的消息类型: {}", wsMessage.getType());
        }
    }

    /**
     * 处理断线重连消息补发
     * 将离线期间的消息追加到对应会话
     *
     * @param wsMessage WebSocket 消息
     */
    @SuppressWarnings("unchecked")
    private void handleReconnectAck(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }

        final List<MessageInfo> missedMessages = (List<MessageInfo>) data.get("missedMessages");
        if (missedMessages == null || missedMessages.isEmpty()) {
            LOG.info("断线重连：没有离线消息");
            return;
        }

        for (final MessageInfo msg : missedMessages) {
            msg.setSentByMe(currentUser.get() != null
                    && currentUser.get().getUserId() != null
                    && currentUser.get().getUserId().equals(msg.getSenderId()));
        }

        // 按会话分组追加消息
        final java.util.Map<String, List<MessageInfo>> grouped = new java.util.HashMap<>();
        for (final MessageInfo msg : missedMessages) {
            grouped.computeIfAbsent(msg.getSessionId(), k -> new ArrayList<>()).add(msg);
        }

        for (final java.util.Map.Entry<String, List<MessageInfo>> entry : grouped.entrySet()) {
            final ChatViewModel chatVm = chatViewModel.get();
            if (chatVm != null && entry.getKey().equals(chatVm.getSessionId())) {
                // 当前会话直接追加
                chatVm.getMessages().addAll(entry.getValue());
            } else {
                // 非当前会话，增加未读数
                for (final ConversationInfo conv : conversations) {
                    if (entry.getKey().equals(conv.getSessionId())) {
                        conv.setUnreadCount(conv.getUnreadCount() + entry.getValue().size());
                        final int index = conversations.indexOf(conv);
                        if (index >= 0) {
                            conversations.set(index, conv);
                        }
                        break;
                    }
                }
            }
        }

        LOG.info("断线重连消息补发完成: count={}", missedMessages.size());
    }

    /**
     * 处理已读回执
     * 更新已发送消息的已读状态
     *
     * @param wsMessage WebSocket 消息
     */
    @SuppressWarnings("unchecked")
    private void handleReadReceipt(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }

        // 服务端返回的 sessionId 和 lastReadMessageId 可能是 Long 类型，安全转换为 String
        final Object sessionIdObj = data.get("sessionId");
        final Object lastReadMsgIdObj = data.get("lastReadMessageId");

        if (sessionIdObj == null || lastReadMsgIdObj == null) {
            return;
        }

        final String sessionId = String.valueOf(sessionIdObj);
        final String lastReadMessageId = String.valueOf(lastReadMsgIdObj);

        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm != null && sessionId.equals(chatVm.getSessionId())) {
            // 更新当前会话中消息的已读状态
            for (final MessageInfo msg : chatVm.getMessages()) {
                if (msg.isSentByMe() && msg.getMessageId() != null
                        && lastReadMessageId.equals(String.valueOf(msg.getMessageId()))) {
                    msg.setRead(true);
                    final int index = chatVm.getMessages().indexOf(msg);
                    if (index >= 0) {
                        chatVm.getMessages().set(index, msg);
                    }
                    break;
                }
            }
        }

        LOG.info("收到已读回执: sessionId={}, lastReadMessageId={}", sessionId, lastReadMessageId);
    }

    /**
     * 处理消息撤回通知
     * 将对应消息标记为已撤回，UI 实时更新为"消息已撤回"
     *
     * @param wsMessage WebSocket 消息
     */
    @SuppressWarnings("unchecked")
    private void handleMessageRecall(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }

        final Object messageIdObj = data.get("messageId");
        final String sessionId = (String) data.get("sessionId");

        if (messageIdObj == null || sessionId == null) {
            return;
        }

        final Long messageId = toLong(messageIdObj);

        // 检查是否当前正在查看的会话
        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm != null && sessionId.equals(chatVm.getSessionId())) {
            for (final MessageInfo msg : chatVm.getMessages()) {
                if (messageId.equals(msg.getMessageId())) {
                    msg.setRecalled(true);
                    final int index = chatVm.getMessages().indexOf(msg);
                    if (index >= 0) {
                        chatVm.getMessages().set(index, msg);
                    }
                    break;
                }
            }
        }

        LOG.info("收到消息撤回通知: sessionId={}, messageId={}", sessionId, messageId);
    }

    /**
     * 处理群成员加入通知
     * 在会话列表中刷新未读数提示，如果当前在群成员页面则需刷新
     */
    @SuppressWarnings("unchecked")
    private void handleGroupMemberJoin(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }
        final Long groupId = toLong(data.get("groupId"));
        final Long userId = toLong(data.get("userId"));
        final String username = (String) data.get("username");
        final String avatar = (String) data.get("avatar");
        LOG.info("群成员加入: groupId={}, userId={}, username={}", groupId, userId, username);

        // 构造系统消息
        final String sessionId = "g_" + groupId;
        final String content = (username != null ? username : "未知用户") + " 已加入群聊";
        final MessageInfo sysMsg = new MessageInfo();
        sysMsg.setMessageId(-System.currentTimeMillis());
        sysMsg.setSessionId(sessionId);
        sysMsg.setSenderId(-1L);
        sysMsg.setSenderName("");
        sysMsg.setSenderAvatar("");
        sysMsg.setSenderType("SYSTEM");
        sysMsg.setType("SYSTEM");
        sysMsg.setContent(content);
        sysMsg.setCreateTime(java.time.LocalDateTime.now());
        sysMsg.setSentByMe(false);

        // 追加到聊天区（如果正在查看）或加入待注入队列
        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm != null && sessionId.equals(chatVm.getSessionId())) {
            chatVm.appendMessage(sysMsg);
        } else {
            // 用户未查看该群时，进入待注入队列，待下次打开群聊时自动注入
            ChatViewModel.addPendingSystemMessage(sessionId, sysMsg);
        }

        // 确保群会话出现在会话列表中
        ensureGroupConversation(groupId, sessionId, content, avatar);

        // 通知群组面板刷新成员列表
        GroupListViewModel.notifyMemberChanged(groupId);
    }

    /**
     * 确保群会话在会话列表中可见（不存在则动态添加）
     */
    private void ensureGroupConversation(final Long groupId, final String sessionId,
                                          final String content, final String avatar) {
        // 检查是否已在列表中
        for (final ConversationInfo conv : conversations) {
            if (sessionId.equals(conv.getSessionId())) {
                conv.setLastMessage(content);
                conv.setLastMessageTime(java.time.LocalDateTime.now());
                final int index = conversations.indexOf(conv);
                if (index >= 0) {
                    conversations.set(index, conv);
                }
                return;
            }
        }
        // 不存在则动态添加群会话
        final ConversationInfo newConv = new ConversationInfo();
        newConv.setSessionId(sessionId);
        newConv.setTargetId(groupId);
        newConv.setTargetType("GROUP");
        newConv.setTargetName("群聊");  // 及时显示，名称后续刷新会话列表时更新
        newConv.setTargetAvatar(avatar);
        newConv.setLastMessage(content);
        newConv.setLastMessageType("SYSTEM");
        newConv.setLastMessageTime(java.time.LocalDateTime.now());
        newConv.setUnreadCount(1);
        // 插入到列表最前面
        conversations.add(0, newConv);
        LOG.info("动态添加群会话: groupId={}, sessionId={}", groupId, sessionId);
    }

    /**
     * 处理群成员离开通知
     */
    @SuppressWarnings("unchecked")
    private void handleGroupMemberLeave(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }
        final Long groupId = toLong(data.get("groupId"));
        final Long userId = toLong(data.get("userId"));
        LOG.info("群成员离开: groupId={}, userId={}", groupId, userId);
        // 通知群组面板刷新成员列表（踢人后实时更新）
        GroupListViewModel.notifyMemberChanged(groupId);
    }

    /**
     * 处理群成员角色变更通知（管理员设置/取消、群主转让）
     */
    @SuppressWarnings("unchecked")
    private void handleGroupMemberRoleChange(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }
        final Long groupId = toLong(data.get("groupId"));
        final Long userId = toLong(data.get("userId"));
        final String newRole = (String) data.get("newRole");
        LOG.info("群成员角色变更: groupId={}, userId={}, newRole={}", groupId, userId, newRole);
        // 通知群组面板刷新成员列表和按钮状态
        GroupListViewModel.notifyMemberChanged(groupId);
    }

    /**
     * 处理群信息变更通知（群名、公告等修改）
     */
    @SuppressWarnings("unchecked")
    private void handleGroupInfoChange(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }
        final Long groupId = toLong(data.get("groupId"));
        LOG.info("群信息变更: groupId={}，刷新会话列表", groupId);
        // 刷新会话列表以获取最新群信息
        loadConversations();
    }

    /**
     * 处理群解散通知
     */
    @SuppressWarnings("unchecked")
    private void handleGroupDismissed(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }
        final Long groupId = toLong(data.get("groupId"));
        LOG.info("群组已解散: groupId={}", groupId);
        // 从会话列表中移除被解散的群
        final String sessionId = "g_" + groupId;
        allConversations.removeIf(conv -> sessionId.equals(conv.getSessionId()));
        conversations.removeIf(conv -> sessionId.equals(conv.getSessionId()));
        // 如果当前正在查看该群，清空聊天区
        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm != null && sessionId.equals(chatVm.getSessionId())) {
            chatViewModel.set(null);
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
        final String thumbnailUrl = (String) data.get("thumbnailUrl");
        final Integer width = data.get("width") != null ? toLong(data.get("width")).intValue() : null;
        final Integer height = data.get("height") != null ? toLong(data.get("height")).intValue() : null;

        final MessageInfo message = new MessageInfo();
        message.setMessageId(messageId);
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setSenderAvatar(senderAvatar);
        message.setSenderType(senderType);
        message.setType(msgType);
        message.setContent(content);
        message.setThumbnailUrl(thumbnailUrl);
        message.setWidth(width);
        message.setHeight(height);
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
            // 当前会话收到消息时自动上报已读
            chatVm.reportRead();
        } else {
            // 非当前会话，增加未读数
            for (final ConversationInfo conv : conversations) {
                if (sessionId.equals(conv.getSessionId())) {
                    conv.setUnreadCount(conv.getUnreadCount() + 1);
                    final int index = conversations.indexOf(conv);
                    if (index >= 0) {
                        conversations.set(index, conv);
                    }
                    // 同步更新 allConversations
                    final int allIndex = allConversations.indexOf(conv);
                    if (allIndex >= 0) {
                        allConversations.set(allIndex, conv);
                    }
                    break;
                }
            }
        }

        LOG.info("收到消息: sessionId={}, messageId={}", sessionId, messageId);
    }

    /**
     * 处理群聊消息
     * 群消息通过 GROUP_MESSAGE 类型推送，需解析并追加到对应会话
     *
     * @param wsMessage WebSocket 消息
     */
    @SuppressWarnings("unchecked")
    private void handleGroupMessage(final WebSocketMessage wsMessage) {
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
        final String thumbnailUrl = (String) data.get("thumbnailUrl");
        final Integer width = data.get("width") != null ? toLong(data.get("width")).intValue() : null;
        final Integer height = data.get("height") != null ? toLong(data.get("height")).intValue() : null;

        final MessageInfo message = new MessageInfo();
        message.setMessageId(messageId);
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setSenderAvatar(senderAvatar);
        message.setSenderType(senderType);
        message.setType(msgType);
        message.setContent(content);
        message.setThumbnailUrl(thumbnailUrl);
        message.setWidth(width);
        message.setHeight(height);
        message.setCreateTime(parseDateTime(createTimeStr));
        message.setSentByMe(currentUser.get() != null
                && currentUser.get().getUserId() != null
                && currentUser.get().getUserId().equals(senderId));

        // 更新会话最后消息
        updateConversationLastMessage(sessionId, content);

        // 如果当前正在查看该群，追加消息到聊天区
        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm != null && sessionId.equals(chatVm.getSessionId())) {
            chatVm.appendMessage(message);
            // 当前会话收到消息时自动上报已读
            chatVm.reportRead();
        } else {
            // 非当前会话，增加未读数
            for (final ConversationInfo conv : conversations) {
                if (sessionId.equals(conv.getSessionId())) {
                    conv.setUnreadCount(conv.getUnreadCount() + 1);
                    final int index = conversations.indexOf(conv);
                    if (index >= 0) {
                        conversations.set(index, conv);
                    }
                    // 同步更新 allConversations
                    final int allIndex = allConversations.indexOf(conv);
                    if (allIndex >= 0) {
                        allConversations.set(allIndex, conv);
                    }
                    break;
                }
            }
        }

        LOG.info("收到群消息: sessionId={}, messageId={}, msgType={}", sessionId, messageId, msgType);
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
            try {
                chatVm.updateMessageAck(clientId, messageId, parseDateTime(createTimeStr));
                LOG.debug("消息确认: clientId={}, messageId={}", clientId, messageId);
            } catch (final Exception e) {
                LOG.error("处理消息确认时异常: clientId={}, messageId={}", clientId, messageId, e);
            }
        } else {
            LOG.debug("消息确认时 ChatViewModel 为 null: clientId={}, messageId={}", clientId, messageId);
        }
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
     * @param content   消息内容
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

    /**
     * 过滤会话列表
     * 根据关键词模糊匹配会话名称，空关键词时恢复全部
     *
     * @param keyword 搜索关键词
     */
    public void filterConversations(final String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            conversations.setAll(allConversations);
            return;
        }

        final String lowerKeyword = keyword.trim().toLowerCase();
        final List<ConversationInfo> filtered = new ArrayList<>();
        for (final ConversationInfo conv : allConversations) {
            if (conv.getTargetName() != null
                    && conv.getTargetName().toLowerCase().contains(lowerKeyword)) {
                filtered.add(conv);
            }
        }
        conversations.setAll(filtered);
        LOG.info("会话搜索: keyword={}, result={}", keyword, filtered.size());
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

    public StringProperty searchKeywordProperty() {
        return searchKeyword;
    }

    public void setOnLogout(final Consumer<Void> callback) {
        this.onLogout = callback;
    }
}
