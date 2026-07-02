package org.example.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.example.client.model.ConversationInfo;
import org.example.client.model.LoginResponse;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.example.client.service.ChatService;
import org.example.client.service.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;

import javafx.application.Platform;
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
 * <br>
 * 请勿在此类中添加新的职责，应拆分为：
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

    /** 被踢下线回调（参数为原因描述） */
    private Consumer<String> onKickedOut;

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
     * 加载会话列表（从服务端获取全部数据 + 本地AI会话）
     */
    private void fetchConversationsFromServer() {
        loading.set(true);
        errorMessage.set("");

        ChatService.getInstance().getConversations()
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        loading.set(false);

                        if (response != null && response.isSuccess()) {
                            final List<ConversationInfo> list = response.getData() != null
                                    ? response.getData().getList()
                                    : new ArrayList<>();

                            // 从 LocalAiEngine 获取所有 AI 角色，构建 AI 会话信息
                            final List<ConversationInfo> aiConversations = new ArrayList<>();
                            try {
                                final UserInfo user = currentUser.get();
                                if (user != null && user.getUserId() != null) {
                                    final List<com.voluntary.chat.server.entity.AiProfile> aiProfiles =
                                            org.example.client.engine.LocalAiEngine.getInstance()
                                                    .listAiProfiles(user.getUserId());

                                    for (final com.voluntary.chat.server.entity.AiProfile profile : aiProfiles) {
                                        final ConversationInfo aiConv = new ConversationInfo();
                                        aiConv.setSessionId("a_" + profile.getId()); // AI会话ID格式
                                        aiConv.setTargetId(profile.getId());
                                        aiConv.setTargetType("AI");
                                        aiConv.setTargetName(profile.getName());
                                        aiConv.setTargetAvatar(profile.getAvatar());
                                        aiConv.setUnreadCount(0); // AI会话默认无未读
                                        aiConv.setLastMessage(null); // 最后消息从历史加载
                                        aiConv.setLastMessageTime(null);
                                        aiConversations.add(aiConv);
                                    }
                                    LOG.info("从本地引擎加载AI会话: count={}", aiConversations.size());
                                }
                            } catch (final Exception e) {
                                LOG.warn("加载AI角色列表失败，跳过AI会话", e);
                            }

                            allConversations.setAll(list);

                            // 合并服务端列表和AI会话
                            final List<ConversationInfo> mergedList = new ArrayList<>(list);
                            mergedList.addAll(0, aiConversations); // AI会话放在顶部

                            // 如果有搜索关键词，应用过滤；否则显示全部
                            final String kw = searchKeyword.get();
                            if (kw != null && !kw.trim().isEmpty()) {
                                filterConversations(kw);
                            } else {
                                conversations.setAll(mergedList);
                            }
                            LOG.info("会话列表加载成功: count={}, AI会话数={}", list.size(), aiConversations.size());
                        } else {
                            final String msg = response != null ? response.getMessage() : "加载会话列表失败";
                            errorMessage.set(msg);
                            LOG.warn("会话列表加载失败: {}", msg);
                        }
                    });
                });
    }

    /**
     * 加载会话列表（带搜索关键词）- 已废弃，改用本地过滤
     *
     * @param keyword 搜索关键词，为空则返回全部
     * @deprecated 使用 {@link #loadConversations()} +
     *             {@link #filterConversations(String)} 替代
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

        LOG.info("[WS-RECV] 收到WebSocket消息: type={}, messageId={}", wsMessage.getType(), wsMessage.getId());

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
            // AI 流式输出
            case MessageTypes.AI_STREAM -> handleAiStream(wsMessage);
            case MessageTypes.FORCE_LOGOUT -> handleForceLogout(wsMessage);
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
            // 更新当前会话中所有 <= lastReadMessageId 的已发送消息为已读
            final Long lastReadId = Long.parseLong(lastReadMessageId);
            final ObservableList<MessageInfo> msgList = chatVm.getMessages();
            for (int i = 0; i < msgList.size(); i++) {
                final MessageInfo msg = msgList.get(i);
                if (msg.isSentByMe() && msg.getMessageId() != null
                        && msg.getMessageId() <= lastReadId && !msg.isRead()) {
                    msg.setRead(true);
                    // 替换元素触发 ObservableList 更新事件
                    msgList.set(i, msg);
                }
            }
        }

        LOG.info("收到已读回执: sessionId={}, lastReadMessageId={}", sessionId, lastReadMessageId);
    }

    /**
     * 处理强制下线通知（账号在其他设备登录）
     * 清理数据、停止重连，触发前端弹窗提示并跳转到登录页
     */
    @SuppressWarnings("unchecked")
    private void handleForceLogout(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        final String reason = data != null && data.get("reason") != null
                ? String.valueOf(data.get("reason"))
                : "您的账号在其他设备登录";

        LOG.warn("收到强制下线通知: reason={}", reason);

        // 关闭 WebSocket、清理本地数据
        WebSocketClient.getInstance().close();
        currentUser.set(null);
        conversations.clear();
        selectedConversation.set(null);
        chatViewModel.set(null);

        // 触发 UI 弹窗并跳转登录页
        if (onKickedOut != null) {
            onKickedOut.accept("您的账户在别处登录，如果不是本人操作，请立即修改密码。");
        } else if (onLogout != null) {
            onLogout.accept(null);
        }
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
        newConv.setTargetName("群聊"); // 临时占位，立即刷新获取真实名称
        newConv.setTargetAvatar(avatar);
        newConv.setLastMessage(content);
        newConv.setLastMessageType("SYSTEM");
        newConv.setLastMessageTime(java.time.LocalDateTime.now());
        newConv.setUnreadCount(1);
        // 插入到列表最前面
        conversations.add(0, newConv);
        LOG.info("动态添加群会话: groupId={}, sessionId={}，正在刷新获取群名称...", groupId, sessionId);
        // 立即刷新会话列表以获取正确的群名称
        Platform.runLater(this::loadConversations);
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
        final String username = (String) data.get("username");
        final String reason = (String) data.get("reason");
        LOG.info("群成员离开: groupId={}, userId={}, username={}, reason={}", groupId, userId, username, reason);

        // 根据离开原因构造不同的系统消息
        final String sessionId = "g_" + groupId;
        final String content;
        if ("KICKED".equals(reason)) {
            content = (username != null ? username : "未知用户") + " 已被移出群聊";
        } else {
            content = (username != null ? username : "未知用户") + " 已退出群聊";
        }
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

        // 更新会话列表中的最后一条消息
        for (final ConversationInfo conv : conversations) {
            if (sessionId.equals(conv.getSessionId())) {
                conv.setLastMessage(content);
                conv.setLastMessageTime(java.time.LocalDateTime.now());
                conv.setLastMessageType("SYSTEM");
                final int index = conversations.indexOf(conv);
                if (index >= 0) {
                    conversations.set(index, conv);
                }
                break;
            }
        }

        // 通知群组面板刷新成员列表
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
     * 处理 AI 流式输出
     * 将 AI 增量内容追加到当前聊天区的消息中
     */
    @SuppressWarnings("unchecked")
    private void handleAiStream(final WebSocketMessage wsMessage) {
        final java.util.Map<String, Object> data = (java.util.Map<String, Object>) wsMessage.getData();
        if (data == null) {
            return;
        }

        final String messageId = String.valueOf(data.get("messageId"));
        final String sessionId = (String) data.get("sessionId");
        final String content = (String) data.get("content");
        final boolean done = Boolean.TRUE.equals(data.get("done"));
        final Object aiMessageIdObj = data.get("aiMessageId");
        final String senderType = (String) data.get("senderType");
        final String senderName = (String) data.get("senderName");
        final String senderAvatar = (String) data.get("senderAvatar");
        final Object senderIdObj = data.get("senderId");

        LOG.debug("[AI-STREAM] 收到AI流式消息: messageId={}, sessionId={}, done={}, contentLen={}",
                messageId, sessionId, done, content != null ? content.length() : 0);

        final ChatViewModel chatVm = chatViewModel.get();
        if (chatVm == null) {
            LOG.debug("[AI-STREAM] 当前无活跃聊天，忽略AI流式消息");
            return;
        }

        final String currentSessionId = chatVm.getSessionId();
        if (currentSessionId == null) {
            return;
        }

        final boolean isGroupAi = sessionId != null && sessionId.contains("_a_");
        final String matchedSessionId = isGroupAi
                ? extractGroupSessionId(sessionId)
                : sessionId;

        if (!currentSessionId.equals(matchedSessionId)) {
            LOG.debug("[AI-STREAM] 会话不匹配，忽略: current={}, target={}",
                    currentSessionId, matchedSessionId);
            return;
        }

        final Long senderId = senderIdObj != null ? toLong(senderIdObj) : null;
        chatVm.handleGroupAiStream(messageId, content, done,
                aiMessageIdObj != null ? toLong(aiMessageIdObj) : null,
                senderId, senderName, senderAvatar, senderType);
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

        // FILE 类型消息：构建 extra 字段，包含文件URL和大小信息
        if ("FILE".equals(msgType)) {
            final String fileUrl = (String) data.get("fileUrl");
            final Long fileSize = data.get("fileSize") != null ? toLong(data.get("fileSize")) : null;
            final String fileName = (String) data.get("fileName");
            if (fileUrl != null || fileSize != null) {
                final StringBuilder extraBuilder = new StringBuilder("{");
                boolean hasField = false;
                if (fileUrl != null) {
                    extraBuilder.append("\"fileUrl\": \"").append(fileUrl).append("\"");
                    hasField = true;
                }
                if (fileSize != null) {
                    if (hasField) {
                        extraBuilder.append(", ");
                    }
                    extraBuilder.append("\"fileSize\": ").append(fileSize);
                    hasField = true;
                }
                if (fileName != null) {
                    if (hasField) {
                        extraBuilder.append(", ");
                    }
                    extraBuilder.append("\"fileName\": \"")
                            .append(fileName.replace("\\", "\\\\").replace("\"", "\\\""))
                            .append("\"");
                }
                extraBuilder.append("}");
                message.setExtra(extraBuilder.toString());
            }
        }

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

        // FILE 类型消息：构建 extra 字段，包含文件URL和大小信息
        if ("FILE".equals(msgType)) {
            final String fileUrl = (String) data.get("fileUrl");
            final Long fileSize = data.get("fileSize") != null ? toLong(data.get("fileSize")) : null;
            final String fileName = (String) data.get("fileName");
            if (fileUrl != null || fileSize != null) {
                final StringBuilder extraBuilder = new StringBuilder("{");
                boolean hasField = false;
                if (fileUrl != null) {
                    extraBuilder.append("\"fileUrl\": \"").append(fileUrl).append("\"");
                    hasField = true;
                }
                if (fileSize != null) {
                    if (hasField) {
                        extraBuilder.append(", ");
                    }
                    extraBuilder.append("\"fileSize\": ").append(fileSize);
                    hasField = true;
                }
                if (fileName != null) {
                    if (hasField) {
                        extraBuilder.append(", ");
                    }
                    extraBuilder.append("\"fileName\": \"")
                            .append(fileName.replace("\\", "\\\\").replace("\"", "\\\""))
                            .append("\"");
                }
                extraBuilder.append("}");
                message.setExtra(extraBuilder.toString());
            }
        }

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
            } catch (final Exception e) {
                LOG.error("处理消息确认时异常: clientId={}, messageId={}", clientId, messageId, e);
            }
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
        TokenStorage.clear();
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

    /**
     * 更新当前用户信息
     *
     * @param user 新的用户信息
     */
    public void updateCurrentUser(final UserInfo user) {
        if (user != null) {
            currentUser.set(user);
            LOG.info("用户信息已更新: userId={}", user.getUserId());
        }
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

    /**
     * 设置被踢下线回调
     *
     * @param callback 参数为弹窗提示文字
     */
    public void setOnKickedOut(final Consumer<String> callback) {
        this.onKickedOut = callback;
    }
}
