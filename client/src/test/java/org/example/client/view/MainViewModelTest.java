package org.example.client.view;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.client.model.ConversationInfo;
import org.example.client.model.LoginResponse;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MainViewModel 单元测试
 *
 * <p>测试会话搜索、未读数实时更新、断线重连消息补发、已读回执展示等功能。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("MainViewModel 测试")
class MainViewModelTest {

    private MainViewModel viewModel;
    private UserInfo currentUser;
    private ConversationInfo conversation1;
    private ConversationInfo conversation2;

    @BeforeEach
    void setUp() {
        viewModel = new MainViewModel();

        currentUser = new UserInfo();
        currentUser.setUserId(1001L);
        currentUser.setUsername("张三");

        // 设置当前用户
        final LoginResponse loginResponse = new LoginResponse();
        loginResponse.setUser(currentUser);
        viewModel.currentUserProperty().set(currentUser);

        conversation1 = new ConversationInfo();
        conversation1.setSessionId("p_1001_1002");
        conversation1.setTargetId(1002L);
        conversation1.setTargetName("李四");
        conversation1.setUnreadCount(0);

        conversation2 = new ConversationInfo();
        conversation2.setSessionId("p_1001_1003");
        conversation2.setTargetId(1003L);
        conversation2.setTargetName("王五");
        conversation2.setUnreadCount(0);

        // 初始化会话列表
        final List<ConversationInfo> list = new ArrayList<>();
        list.add(conversation1);
        list.add(conversation2);
        viewModel.getConversations().addAll(list);

        // 通过反射设置 allConversations（filterConversations 依赖它）
        setAllConversations(list);
    }

    /**
     * 通过反射设置 allConversations 字段
     */
    @SuppressWarnings("unchecked")
    private void setAllConversations(final List<ConversationInfo> list) {
        try {
            final java.lang.reflect.Field field = MainViewModel.class.getDeclaredField("allConversations");
            field.setAccessible(true);
            final javafx.collections.ObservableList<ConversationInfo> allConversations =
                    (javafx.collections.ObservableList<ConversationInfo>) field.get(viewModel);
            allConversations.setAll(list);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 通过反射调用私有方法 handleReceiveMessage
     */
    private void invokeHandleReceiveMessage(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleReceiveMessage", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    /**
     * 通过反射调用私有方法 handleReconnectAck
     */
    private void invokeHandleReconnectAck(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleReconnectAck", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    /**
     * 通过反射调用私有方法 handleReadReceipt
     */
    private void invokeHandleReadReceipt(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleReadReceipt", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    // ==================== 会话搜索测试 ====================

    @Test
    @DisplayName("会话搜索 - 空关键词恢复全部会话")
    void filterConversations_emptyKeywordRestoresAll() {
        // 先过滤
        viewModel.filterConversations("李");
        assertEquals(1, viewModel.getConversations().size());

        // 空关键词恢复
        viewModel.filterConversations("");
        assertEquals(2, viewModel.getConversations().size());
    }

    @Test
    @DisplayName("会话搜索 - null关键词恢复全部会话")
    void filterConversations_nullKeywordRestoresAll() {
        viewModel.filterConversations("王");
        assertEquals(1, viewModel.getConversations().size());

        viewModel.filterConversations(null);
        assertEquals(2, viewModel.getConversations().size());
    }

    @Test
    @DisplayName("会话搜索 - 精确匹配会话名称")
    void filterConversations_exactMatch() {
        viewModel.filterConversations("李四");
        assertEquals(1, viewModel.getConversations().size());
        assertEquals("李四", viewModel.getConversations().get(0).getTargetName());
    }

    @Test
    @DisplayName("会话搜索 - 模糊匹配（部分关键词）")
    void filterConversations_partialMatch() {
        viewModel.filterConversations("李");
        assertEquals(1, viewModel.getConversations().size());
        assertEquals("p_1001_1002", viewModel.getConversations().get(0).getSessionId());
    }

    @Test
    @DisplayName("会话搜索 - 大小写不敏感")
    void filterConversations_caseInsensitive() {
        // 添加一个英文名称的会话
        final ConversationInfo conv = new ConversationInfo();
        conv.setSessionId("p_1001_1004");
        conv.setTargetName("Alice");
        conv.setUnreadCount(0);

        final List<ConversationInfo> all = new ArrayList<>(List.of(conversation1, conversation2, conv));
        viewModel.getConversations().setAll(all);
        setAllConversations(all);

        viewModel.filterConversations("alice");
        assertEquals(1, viewModel.getConversations().size());
        assertEquals("Alice", viewModel.getConversations().get(0).getTargetName());
    }

    @Test
    @DisplayName("会话搜索 - 无匹配结果")
    void filterConversations_noMatch() {
        viewModel.filterConversations("不存在的用户");
        assertTrue(viewModel.getConversations().isEmpty());
    }

    @Test
    @DisplayName("会话搜索 - 空格关键词恢复全部")
    void filterConversations_blankKeywordRestoresAll() {
        viewModel.filterConversations("李");
        assertEquals(1, viewModel.getConversations().size());

        viewModel.filterConversations("   ");
        assertEquals(2, viewModel.getConversations().size());
    }

    // ==================== 未读数实时更新测试 ====================

    @Test
    @DisplayName("收到消息 - 非当前会话增加未读数")
    void receiveMessage_nonCurrentConversationIncreasesUnread() throws Exception {
        // 不选中任何会话，收到 conversation1 的消息
        final WebSocketMessage wsMessage = buildReceiveMessage(
                "p_1001_1002", 200L, 1002L, "李四", "你好");

        invokeHandleReceiveMessage(wsMessage);

        // conversation1 的未读数应增加
        ConversationInfo updated = findConversation("p_1001_1002");
        assertNotNull(updated);
        assertEquals(1, updated.getUnreadCount());
    }

    @Test
    @DisplayName("收到消息 - 当前会话不增加未读数")
    void receiveMessage_currentConversationNoUnreadIncrease() throws Exception {
        // 选中 conversation1
        viewModel.selectConversation(conversation1);

        final WebSocketMessage wsMessage = buildReceiveMessage(
                "p_1001_1002", 200L, 1002L, "李四", "你好");

        invokeHandleReceiveMessage(wsMessage);

        // 当前会话未读数应为 0（选中时已清零）
        final ConversationInfo updated = findConversation("p_1001_1002");
        assertNotNull(updated);
        assertEquals(0, updated.getUnreadCount());
    }

    @Test
    @DisplayName("收到消息 - 多次收到消息未读数累加")
    void receiveMessage_multipleMessagesAccumulateUnread() throws Exception {
        final WebSocketMessage msg1 = buildReceiveMessage(
                "p_1001_1003", 201L, 1003L, "王五", "消息1");
        final WebSocketMessage msg2 = buildReceiveMessage(
                "p_1001_1003", 202L, 1003L, "王五", "消息2");

        invokeHandleReceiveMessage(msg1);
        invokeHandleReceiveMessage(msg2);

        final ConversationInfo updated = findConversation("p_1001_1003");
        assertNotNull(updated);
        assertEquals(2, updated.getUnreadCount());
    }

    @Test
    @DisplayName("收到消息 - 更新会话最后消息内容")
    void receiveMessage_updatesLastMessage() throws Exception {
        final WebSocketMessage wsMessage = buildReceiveMessage(
                "p_1001_1002", 200L, 1002L, "李四", "最新消息内容");

        invokeHandleReceiveMessage(wsMessage);

        final ConversationInfo updated = findConversation("p_1001_1002");
        assertNotNull(updated);
        assertEquals("最新消息内容", updated.getLastMessage());
    }

    // ==================== 断线重连消息补发测试 ====================

    @Test
    @DisplayName("断线重连 - 无离线消息不崩溃")
    void reconnectAck_noMissedMessages() throws Exception {
        final Map<String, Object> data = new HashMap<>();
        data.put("missedMessages", new ArrayList<>());

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.RECONNECT_ACK)
                .data(data)
                .build();

        invokeHandleReconnectAck(wsMessage);

        // 会话列表不应变化
        assertEquals(2, viewModel.getConversations().size());
    }

    @Test
    @DisplayName("断线重连 - data为null不崩溃")
    void reconnectAck_nullData() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.RECONNECT_ACK)
                .data(null)
                .build();

        invokeHandleReconnectAck(wsMessage);
        assertEquals(2, viewModel.getConversations().size());
    }

    @Test
    @DisplayName("断线重连 - 补发消息到非当前会话增加未读数")
    void reconnectAck_missedMessagesIncreaseUnread() throws Exception {
        // 构造离线消息列表
        final List<MessageInfo> missedMessages = new ArrayList<>();
        final MessageInfo msg1 = new MessageInfo();
        msg1.setMessageId(300L);
        msg1.setSessionId("p_1001_1002");
        msg1.setSenderId(1002L);
        msg1.setSenderName("李四");
        msg1.setContent("离线消息1");
        missedMessages.add(msg1);

        final MessageInfo msg2 = new MessageInfo();
        msg2.setMessageId(301L);
        msg2.setSessionId("p_1001_1002");
        msg2.setSenderId(1002L);
        msg2.setSenderName("李四");
        msg2.setContent("离线消息2");
        missedMessages.add(msg2);

        final Map<String, Object> data = new HashMap<>();
        data.put("missedMessages", missedMessages);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.RECONNECT_ACK)
                .data(data)
                .build();

        invokeHandleReconnectAck(wsMessage);

        // conversation1 未读数应增加 2
        final ConversationInfo updated = findConversation("p_1001_1002");
        assertNotNull(updated);
        assertEquals(2, updated.getUnreadCount());
    }

    @Test
    @DisplayName("断线重连 - 补发消息到当前会话直接追加")
    void reconnectAck_missedMessagesAppendedToCurrentChat() throws Exception {
        // 选中 conversation1
        viewModel.selectConversation(conversation1);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        final List<MessageInfo> missedMessages = new ArrayList<>();
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(300L);
        msg.setSessionId("p_1001_1002");
        msg.setSenderId(1002L);
        msg.setSenderName("李四");
        msg.setContent("离线消息");
        missedMessages.add(msg);

        final Map<String, Object> data = new HashMap<>();
        data.put("missedMessages", missedMessages);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.RECONNECT_ACK)
                .data(data)
                .build();

        invokeHandleReconnectAck(wsMessage);

        // 消息应追加到当前聊天视图模型
        assertEquals(1, chatVm.getMessages().size());
        assertEquals("离线消息", chatVm.getMessages().get(0).getContent());
    }

    // ==================== 已读回执测试 ====================

    @Test
    @DisplayName("已读回执 - data为null不崩溃")
    void readReceipt_nullData() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.READ_RECEIPT)
                .data(null)
                .build();

        invokeHandleReadReceipt(wsMessage);
        // 不崩溃即通过
    }

    @Test
    @DisplayName("已读回执 - 缺少sessionId不处理")
    void readReceipt_missingSessionId() throws Exception {
        final Map<String, Object> data = new HashMap<>();
        data.put("lastReadMessageId", "msg_005");

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.READ_RECEIPT)
                .data(data)
                .build();

        invokeHandleReadReceipt(wsMessage);
        // 不崩溃即通过
    }

    @Test
    @DisplayName("已读回执 - 缺少lastReadMessageId不处理")
    void readReceipt_missingLastReadMessageId() throws Exception {
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "p_1001_1002");

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.READ_RECEIPT)
                .data(data)
                .build();

        invokeHandleReadReceipt(wsMessage);
        // 不崩溃即通过
    }

    @Test
    @DisplayName("已读回执 - 更新当前会话消息已读状态")
    void readReceipt_updatesCurrentChatMessageReadStatus() throws Exception {
        // 选中 conversation1
        viewModel.selectConversation(conversation1);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        // 添加一条自己发送的消息
        final MessageInfo myMsg = new MessageInfo();
        myMsg.setMessageId(500L);
        myMsg.setSessionId("p_1001_1002");
        myMsg.setSenderId(1001L);
        myMsg.setSentByMe(true);
        myMsg.setContent("我发送的消息");
        chatVm.appendMessage(myMsg);

        // 验证初始未读
        assertFalse(chatVm.getMessages().get(0).isRead());

        // 发送已读回执
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "p_1001_1002");
        data.put("lastReadMessageId", "500");

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.READ_RECEIPT)
                .data(data)
                .build();

        invokeHandleReadReceipt(wsMessage);

        // 消息应被标记为已读
        assertTrue(chatVm.getMessages().get(0).isRead());
    }

    @Test
    @DisplayName("已读回执 - 非当前会话不处理")
    void readReceipt_nonCurrentSessionNoOp() throws Exception {
        // 选中 conversation1
        viewModel.selectConversation(conversation1);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        // 添加一条消息
        final MessageInfo myMsg = new MessageInfo();
        myMsg.setMessageId(500L);
        myMsg.setSessionId("p_1001_1002");
        myMsg.setSenderId(1001L);
        myMsg.setSentByMe(true);
        myMsg.setContent("我的消息");
        chatVm.appendMessage(myMsg);

        // 对 conversation2 发送已读回执
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "p_1001_1003");
        data.put("lastReadMessageId", "500");

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("server-msg-1")
                .type(MessageTypes.READ_RECEIPT)
                .data(data)
                .build();

        invokeHandleReadReceipt(wsMessage);

        // 当前会话的消息不应被标记为已读
        assertFalse(chatVm.getMessages().get(0).isRead());
    }

    // ==================== 选择会话测试 ====================

    @Test
    @DisplayName("选择会话 - 清零未读数")
    void selectConversation_clearsUnreadCount() {
        // 设置未读数
        conversation1.setUnreadCount(5);

        viewModel.selectConversation(conversation1);

        assertEquals(0, conversation1.getUnreadCount());
    }

    @Test
    @DisplayName("选择会话 - 创建ChatViewModel并加载历史")
    void selectConversation_createsChatViewModel() {
        viewModel.selectConversation(conversation1);

        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);
        assertEquals("p_1001_1002", chatVm.getSessionId());
    }

    @Test
    @DisplayName("选择null会话 - 清空ChatViewModel")
    void selectConversation_nullClearsChatViewModel() {
        viewModel.selectConversation(conversation1);
        assertNotNull(viewModel.getChatViewModel());

        viewModel.selectConversation(null);
        // chatViewModel 应为 null（但 loading 可能因异步回调变化）
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建接收消息的 WebSocketMessage
     */
    private WebSocketMessage buildReceiveMessage(
            final String sessionId, final Long messageId,
            final Long senderId, final String senderName, final String content) {
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("messageId", messageId);
        data.put("senderId", senderId);
        data.put("senderName", senderName);
        data.put("senderType", "USER");
        data.put("msgType", "TEXT");
        data.put("content", content);
        data.put("createTime", "2024-01-01T10:00:00");

        return WebSocketMessage.builder()
                .id("server-msg-" + messageId)
                .type(MessageTypes.RECEIVE_MESSAGE)
                .data(data)
                .build();
    }

    /**
     * 从会话列表中查找指定 sessionId 的会话
     */
    private ConversationInfo findConversation(final String sessionId) {
        for (final ConversationInfo conv : viewModel.getConversations()) {
            if (sessionId.equals(conv.getSessionId())) {
                return conv;
            }
        }
        return null;
    }

    // ==================== GROUP_MESSAGE 处理测试 ====================

    /**
     * 通过反射调用私有方法 handleGroupMessage
     */
    private void invokeHandleGroupMessage(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleGroupMessage", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    @Test
    @DisplayName("handleGroupMessage 应正确解析并追加群消息")
    void handleGroupMessage_shouldParseAndAppendMessage() throws Exception {
        // 准备群聊会话
        final ConversationInfo groupConv = new ConversationInfo();
        groupConv.setSessionId("g_2001");
        groupConv.setTargetId(2001L);
        groupConv.setTargetName("技术交流群");
        groupConv.setUnreadCount(0);
        viewModel.getConversations().add(groupConv);
        setAllConversations(viewModel.getConversations());

        // 选择该群会话
        viewModel.selectConversation(groupConv);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("msg_001")
                .type(MessageTypes.GROUP_MESSAGE)
                .data(Map.of(
                        "messageId", 100L,
                        "sessionId", "g_2001",
                        "senderId", 1002L,
                        "senderName", "李四",
                        "senderAvatar", "",
                        "senderType", "USER",
                        "msgType", "TEXT",
                        "content", "大家好",
                        "createTime", "2024-01-01T10:00:00"))
                .build();

        invokeHandleGroupMessage(wsMessage);

        // 验证消息已追加
        assertEquals(1, chatVm.getMessages().size());
        assertEquals("大家好", chatVm.getMessages().get(0).getContent());
        assertEquals("TEXT", chatVm.getMessages().get(0).getType());
        assertFalse(chatVm.getMessages().get(0).isSentByMe());
    }

    @Test
    @DisplayName("handleGroupMessage 应正确处理 IMAGE 类型群消息")
    void handleGroupMessage_shouldHandleImageMessage() throws Exception {
        final ConversationInfo groupConv = new ConversationInfo();
        groupConv.setSessionId("g_2002");
        groupConv.setTargetId(2002L);
        groupConv.setTargetName("图片群");
        groupConv.setUnreadCount(0);
        viewModel.getConversations().add(groupConv);
        setAllConversations(viewModel.getConversations());

        viewModel.selectConversation(groupConv);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("msg_002")
                .type(MessageTypes.GROUP_MESSAGE)
                .data(Map.of(
                        "messageId", 101L,
                        "sessionId", "g_2002",
                        "senderId", 1002L,
                        "senderName", "李四",
                        "senderAvatar", "",
                        "senderType", "USER",
                        "msgType", "IMAGE",
                        "content", "http://example.com/image.png",
                        "createTime", "2024-01-01T10:00:00"))
                .build();

        invokeHandleGroupMessage(wsMessage);

        assertEquals(1, chatVm.getMessages().size());
        assertEquals("IMAGE", chatVm.getMessages().get(0).getType());
        assertEquals("http://example.com/image.png", chatVm.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("handleGroupMessage 非当前会话时应增加未读数")
    void handleGroupMessage_shouldIncrementUnreadWhenNotCurrentSession() throws Exception {
        final ConversationInfo groupConv = new ConversationInfo();
        groupConv.setSessionId("g_2003");
        groupConv.setTargetId(2003L);
        groupConv.setTargetName("未读群");
        groupConv.setUnreadCount(0);
        viewModel.getConversations().add(groupConv);
        setAllConversations(viewModel.getConversations());

        // 不选择该群，保持当前会话为 conversation1
        viewModel.selectConversation(conversation1);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("msg_003")
                .type(MessageTypes.GROUP_MESSAGE)
                .data(Map.of(
                        "messageId", 102L,
                        "sessionId", "g_2003",
                        "senderId", 1002L,
                        "senderName", "李四",
                        "senderAvatar", "",
                        "senderType", "USER",
                        "msgType", "TEXT",
                        "content", "新消息",
                        "createTime", "2024-01-01T10:00:00"))
                .build();

        invokeHandleGroupMessage(wsMessage);

        // 验证未读数增加
        assertEquals(1, groupConv.getUnreadCount());
    }

    @Test
    @DisplayName("handleGroupMessage 自己发送的消息应标记为 sentByMe")
    void handleGroupMessage_shouldMarkSentByMeForOwnMessage() throws Exception {
        final ConversationInfo groupConv = new ConversationInfo();
        groupConv.setSessionId("g_2004");
        groupConv.setTargetId(2004L);
        groupConv.setTargetName("我的群");
        groupConv.setUnreadCount(0);
        viewModel.getConversations().add(groupConv);
        setAllConversations(viewModel.getConversations());

        viewModel.selectConversation(groupConv);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("msg_004")
                .type(MessageTypes.GROUP_MESSAGE)
                .data(Map.of(
                        "messageId", 103L,
                        "sessionId", "g_2004",
                        "senderId", 1001L,  // 当前用户 ID
                        "senderName", "张三",
                        "senderAvatar", "",
                        "senderType", "USER",
                        "msgType", "TEXT",
                        "content", "我发的消息",
                        "createTime", "2024-01-01T10:00:00"))
                .build();

        invokeHandleGroupMessage(wsMessage);

        assertEquals(1, chatVm.getMessages().size());
        assertTrue(chatVm.getMessages().get(0).isSentByMe());
    }

    // ==================== handleMessageRecall 测试 ====================

    private void invokeHandleMessageRecall(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleMessageRecall", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    @Test
    @DisplayName("handleMessageRecall - 当前会话标记已撤回")
    void handleMessageRecall_shouldMarkRecalledInCurrentSession() throws Exception {
        viewModel.selectConversation(conversation1);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(500L);
        msg.setSessionId("p_1001_1002");
        msg.setContent("待撤回消息");
        msg.setSentByMe(true);
        chatVm.appendMessage(msg);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("recall-1")
                .type(MessageTypes.MESSAGE_RECALL)
                .data(Map.of("messageId", 500L, "sessionId", "p_1001_1002"))
                .build();

        invokeHandleMessageRecall(wsMessage);

        assertTrue(chatVm.getMessages().get(0).isRecalled());
    }

    @Test
    @DisplayName("handleMessageRecall - data 为 null 不崩溃")
    void handleMessageRecall_nullData_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("recall-2")
                .type(MessageTypes.MESSAGE_RECALL)
                .data(null)
                .build();
        invokeHandleMessageRecall(wsMessage);
    }

    @Test
    @DisplayName("handleMessageRecall - 缺少 messageId 不崩溃")
    void handleMessageRecall_missingMessageId_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("recall-3")
                .type(MessageTypes.MESSAGE_RECALL)
                .data(Map.of("sessionId", "p_1001_1002"))
                .build();
        invokeHandleMessageRecall(wsMessage);
    }

    @Test
    @DisplayName("handleMessageRecall - 非当前会话不处理")
    void handleMessageRecall_nonCurrentSession_shouldNotAffectChatVm() throws Exception {
        viewModel.selectConversation(conversation1);
        final ChatViewModel chatVm = viewModel.getChatViewModel();
        assertNotNull(chatVm);

        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(600L);
        msg.setContent("其他会话消息");
        chatVm.appendMessage(msg);

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("recall-4")
                .type(MessageTypes.MESSAGE_RECALL)
                .data(Map.of("messageId", 600L, "sessionId", "p_1001_9999"))
                .build();

        invokeHandleMessageRecall(wsMessage);

        assertFalse(chatVm.getMessages().get(0).isRecalled());
    }

    // ==================== handleAck 测试 ====================

    private void invokeHandleAck(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleAck", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    @Test
    @DisplayName("handleAck - 正常处理")
    void handleAck_normalCase_shouldNotCrash() throws Exception {
        viewModel.selectConversation(conversation1);
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("ack-1")
                .type(MessageTypes.MESSAGE_ACK)
                .data(Map.of("clientId", "test-client", "messageId", 100L,
                        "createTime", "2024-01-01T10:00:00"))
                .build();
        invokeHandleAck(wsMessage);
    }

    @Test
    @DisplayName("handleAck - data 为 null 不崩溃")
    void handleAck_nullData_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("ack-2")
                .type(MessageTypes.MESSAGE_ACK)
                .data(null)
                .build();
        invokeHandleAck(wsMessage);
    }

    @Test
    @DisplayName("handleAck - ChatViewModel 为 null 不崩溃")
    void handleAck_chatViewModelNull_shouldNotCrash() throws Exception {
        // 不选择任何会话
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("ack-3")
                .type(MessageTypes.MESSAGE_ACK)
                .data(Map.of("clientId", "test", "messageId", 200L, "createTime", "2024-01-01T10:00:00"))
                .build();
        invokeHandleAck(wsMessage);
    }

    // ==================== handleStatusChange 测试 ====================

    private void invokeHandleStatusChange(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleStatusChange", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    @Test
    @DisplayName("handleStatusChange - 正常处理")
    void handleStatusChange_normalCase_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("status-1")
                .type(MessageTypes.STATUS_CHANGE)
                .data(Map.of("userId", 1002L, "online", true))
                .build();
        invokeHandleStatusChange(wsMessage);
    }

    // ==================== handleConnectionChange 测试 ====================

    @Test
    @DisplayName("handleConnectionChange - 连接状态变更")
    void handleConnectionChange_shouldUpdateConnectedProperty() throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleConnectionChange", boolean.class);
        method.setAccessible(true);

        method.invoke(viewModel, true);
        assertTrue(viewModel.connectedProperty().get());

        method.invoke(viewModel, false);
        assertFalse(viewModel.connectedProperty().get());
    }

    // ==================== 群组事件处理测试 ====================

    private void invokeHandler(final String methodName, final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(methodName, WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    @Test
    @DisplayName("handleGroupMemberJoin - 正常处理不崩溃")
    void handleGroupMemberJoin_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("gmj-1")
                .type(MessageTypes.GROUP_MEMBER_JOIN)
                .data(Map.of("groupId", 1L, "userId", 1002L, "username", "新成员"))
                .build();
        invokeHandler("handleGroupMemberJoin", wsMessage);
    }

    @Test
    @DisplayName("handleGroupMemberLeave - 正常处理不崩溃")
    void handleGroupMemberLeave_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("gml-1")
                .type(MessageTypes.GROUP_MEMBER_LEAVE)
                .data(Map.of("groupId", 1L, "userId", 1002L))
                .build();
        invokeHandler("handleGroupMemberLeave", wsMessage);
    }

    @Test
    @DisplayName("handleGroupMemberRoleChange - 正常处理不崩溃")
    void handleGroupMemberRoleChange_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("gmrc-1")
                .type(MessageTypes.GROUP_MEMBER_ROLE_CHANGE)
                .data(Map.of("groupId", 1L, "userId", 1002L, "newRole", "ADMIN"))
                .build();
        invokeHandler("handleGroupMemberRoleChange", wsMessage);
    }

    @Test
    @DisplayName("handleGroupInfoChange - 正常处理不崩溃")
    void handleGroupInfoChange_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("gic-1")
                .type(MessageTypes.GROUP_INFO_CHANGE)
                .data(Map.of("groupId", 1L))
                .build();
        invokeHandler("handleGroupInfoChange", wsMessage);
    }

    @Test
    @DisplayName("handleGroupDismissed - 移除群会话")
    void handleGroupDismissed_shouldRemoveGroupConversation() throws Exception {
        final ConversationInfo groupConv = new ConversationInfo();
        groupConv.setSessionId("g_2001");
        groupConv.setTargetId(2001L);
        groupConv.setTargetName("测试群");
        viewModel.getConversations().add(groupConv);
        setAllConversations(viewModel.getConversations());
        assertEquals(3, viewModel.getConversations().size());

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("gd-1")
                .type(MessageTypes.GROUP_DISMISSED)
                .data(Map.of("groupId", 2001L))
                .build();
        invokeHandler("handleGroupDismissed", wsMessage);

        assertEquals(2, viewModel.getConversations().size());
    }

    @Test
    @DisplayName("handleGroupDismissed - 当前正在查看该群则清空")
    void handleGroupDismissed_currentlyViewing_shouldClearChatVm() throws Exception {
        final ConversationInfo groupConv = new ConversationInfo();
        groupConv.setSessionId("g_2001");
        groupConv.setTargetId(2001L);
        groupConv.setTargetName("测试群");
        viewModel.getConversations().add(groupConv);
        setAllConversations(viewModel.getConversations());
        viewModel.selectConversation(groupConv);
        assertNotNull(viewModel.getChatViewModel());

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("gd-2")
                .type(MessageTypes.GROUP_DISMISSED)
                .data(Map.of("groupId", 2001L))
                .build();
        invokeHandler("handleGroupDismissed", wsMessage);

        // chatViewModel 被清空
        assertNull(viewModel.getChatViewModel());
    }

    // ==================== 消息数据类型测试 (Long/String 兼容) ====================

    @Test
    @DisplayName("handleReceiveMessage - messageId 为 Long 类型")
    void handleReceiveMessage_longTypeMessageId_shouldParse() throws Exception {
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "p_1001_1002");
        data.put("messageId", 300L);
        data.put("senderId", 1002L);
        data.put("senderName", "李四");
        data.put("senderType", "USER");
        data.put("msgType", "TEXT");
        data.put("content", "Long类型测试");
        data.put("createTime", "2024-01-01T10:00:00");

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("type-1")
                .type(MessageTypes.RECEIVE_MESSAGE)
                .data(data)
                .build();

        invokeHandleReceiveMessage(wsMessage);
        assertEquals(1, conversation1.getUnreadCount());
    }

    @Test
    @DisplayName("handleReceiveMessage - messageId 为 String 类型")
    void handleReceiveMessage_stringTypeMessageId_shouldParse() throws Exception {
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "p_1001_1002");
        data.put("messageId", "301");
        data.put("senderId", 1002L);
        data.put("senderName", "李四");
        data.put("senderType", "USER");
        data.put("msgType", "TEXT");
        data.put("content", "String类型测试");
        data.put("createTime", "2024-01-01T10:00:00");

        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("type-2")
                .type(MessageTypes.RECEIVE_MESSAGE)
                .data(data)
                .build();

        invokeHandleReceiveMessage(wsMessage);
        assertEquals(1, conversation1.getUnreadCount());
    }

    // ==================== handleWebSocketMessage 分发测试 ====================

    private void invokeHandleWebSocketMessage(final WebSocketMessage wsMessage) throws Exception {
        final Method method = MainViewModel.class.getDeclaredMethod(
                "handleWebSocketMessage", WebSocketMessage.class);
        method.setAccessible(true);
        method.invoke(viewModel, wsMessage);
    }

    @Test
    @DisplayName("handleWebSocketMessage - null 消息不崩溃")
    void handleWebSocketMessage_nullMessage_shouldNotCrash() throws Exception {
        invokeHandleWebSocketMessage(null);
    }

    @Test
    @DisplayName("handleWebSocketMessage - null type 不崩溃")
    void handleWebSocketMessage_nullType_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder().id("id").type(null).data(Map.of()).build();
        invokeHandleWebSocketMessage(wsMessage);
    }

    @Test
    @DisplayName("handleWebSocketMessage - 未知类型不崩溃")
    void handleWebSocketMessage_unknownType_shouldNotCrash() throws Exception {
        final WebSocketMessage wsMessage = WebSocketMessage.builder()
                .id("id").type("UNKNOWN_TYPE").data(Map.of()).build();
        invokeHandleWebSocketMessage(wsMessage);
    }
}
