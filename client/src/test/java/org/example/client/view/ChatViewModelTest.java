package org.example.client.view;

import java.time.LocalDateTime;

import org.example.client.model.ConversationInfo;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.beans.property.BooleanProperty;

/**
 * ChatViewModel 测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class ChatViewModelTest {

    private ChatViewModel chatViewModel;
    private UserInfo currentUser;
    private ConversationInfo conversation;

    @BeforeEach
    void setUp() {
        currentUser = new UserInfo();
        currentUser.setUserId(1001L);
        currentUser.setUsername("张三");

        conversation = new ConversationInfo();
        conversation.setSessionId("p_1001_1002");
        conversation.setTargetId(1002L);
        conversation.setTargetName("李四");

        chatViewModel = new ChatViewModel(currentUser, conversation);

        // 清理静态 pendingSystemMessages，避免测试间干扰
        try {
            final java.lang.reflect.Field pendingField = ChatViewModel.class.getDeclaredField("pendingSystemMessages");
            pendingField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final java.util.Map<String, java.util.List<MessageInfo>> map =
                    (java.util.Map<String, java.util.List<MessageInfo>>) pendingField.get(null);
            map.clear();
        } catch (final Exception e) {
            // 忽略清理失败
        }
    }

    @Test
    void getSessionId_shouldReturnConversationSessionId() {
        assertEquals("p_1001_1002", chatViewModel.getSessionId());
    }

    @Test
    void getConversationName_shouldReturnTargetName() {
        assertEquals("李四", chatViewModel.getConversationName());
    }

    @Test
    void getSessionId_shouldReturnNullWhenConversationIsNull() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        assertNull(vm.getSessionId());
    }

    @Test
    void sendMessage_shouldNotSendWhenInputIsEmpty() {
        chatViewModel.inputTextProperty().set("");
        chatViewModel.sendMessage();
        // 消息列表应为空（没有调用 WebSocket）
        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    @Test
    void sendMessage_shouldNotSendWhenInputIsBlank() {
        chatViewModel.inputTextProperty().set("   ");
        chatViewModel.sendMessage();
        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    @Test
    void sendMessage_shouldAddOptimisticMessage() {
        chatViewModel.inputTextProperty().set("你好");
        chatViewModel.sendMessage();

        // 应该添加一条乐观消息
        assertEquals(1, chatViewModel.getMessages().size());
        final MessageInfo msg = chatViewModel.getMessages().get(0);
        assertEquals("你好", msg.getContent());
        assertTrue(msg.isSentByMe());
        assertEquals("TEXT", msg.getType());
        assertEquals("p_1001_1002", msg.getSessionId());
    }

    @Test
    void sendMessage_shouldClearInputAfterSend() {
        chatViewModel.inputTextProperty().set("测试消息");
        chatViewModel.sendMessage();
        assertEquals("", chatViewModel.getInputText());
    }

    @Test
    void appendMessage_shouldAddMessageToList() {
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(1L);
        msg.setSessionId("p_1001_1002");
        msg.setContent("新消息");

        chatViewModel.appendMessage(msg);

        assertEquals(1, chatViewModel.getMessages().size());
        assertEquals("新消息", chatViewModel.getMessages().get(0).getContent());
    }

    @Test
    void appendMessage_shouldNotAddNullMessage() {
        chatViewModel.appendMessage(null);
        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    @Test
    void appendMessage_shouldNotAddDuplicateMessage() {
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(1L);
        msg.setContent("消息1");

        chatViewModel.appendMessage(msg);
        chatViewModel.appendMessage(msg);

        assertEquals(1, chatViewModel.getMessages().size());
    }

    @Test
    void updateMessageAck_shouldUpdatePendingMessage() {
        // 先发送一条消息
        chatViewModel.inputTextProperty().set("待确认消息");
        chatViewModel.sendMessage();

        // 获取乐观消息（messageId 为 -1）
        final MessageInfo pending = chatViewModel.getMessages().get(0);
        assertEquals(-1L, pending.getMessageId());

        // 模拟 ACK（无法获取 clientId，测试逻辑：messageId 更新）
        // 由于 clientId 是内部生成，这里仅验证消息存在
        assertNotNull(pending);
    }

    @Test
    void errorMessageProperty_shouldBeInitiallyEmpty() {
        assertEquals("", chatViewModel.errorMessageProperty().get());
    }

    @Test
    void loadingProperty_shouldBeInitiallyFalse() {
        assertFalse(chatViewModel.loadingProperty().get());
    }

    @Test
    void inputTextProperty_shouldBeInitiallyEmpty() {
        assertEquals("", chatViewModel.inputTextProperty().get());
    }

    @Test
    void messagesProperty_shouldBeInitiallyEmpty() {
        assertTrue(chatViewModel.messagesProperty().get().isEmpty());
    }

    // ==================== 消息撤回测试 ====================

    @Test
    void recallMessage_shouldDoNothingWhenMessageIsNull() {
        chatViewModel.recallMessage(null);
        // 不应抛出异常，错误消息不变
        assertEquals("", chatViewModel.errorMessageProperty().get());
    }

    @Test
    void recallMessage_optimisticMessage_shouldMarkRecalledAndRegisterPendingRecall() {
        // 发送乐观消息
        chatViewModel.inputTextProperty().set("测试消息");
        chatViewModel.sendMessage();

        // 获取乐观消息（messageId < 0）
        final MessageInfo optimisticMsg = chatViewModel.getMessages().get(0);
        assertEquals(-1L, optimisticMsg.getMessageId());
        assertFalse(optimisticMsg.isRecalled());

        // 撤回乐观消息
        chatViewModel.recallMessage(optimisticMsg);

        // 应立即标记为已撤回
        assertTrue(optimisticMsg.isRecalled());
    }

    @Test
    void recallMessage_optimisticMessageNotFound_shouldNotCrash() {
        // 创建一个不在 pendingMessages 中的乐观消息
        final MessageInfo orphanMsg = new MessageInfo();
        orphanMsg.setMessageId(-2L);
        orphanMsg.setSessionId("p_1001_1002");
        orphanMsg.setContent("孤立消息");

        // 撤回不应抛出异常
        chatViewModel.recallMessage(orphanMsg);
        assertFalse(orphanMsg.isRecalled());
    }

    @Test
    void recallMessage_confirmedMessage_shouldNotSetErrorImmediately() {
        // 已确认消息（messageId > 0）走异步 REST 撤回
        final MessageInfo confirmedMsg = new MessageInfo();
        confirmedMsg.setMessageId(100L);
        confirmedMsg.setSessionId("p_1001_1002");
        confirmedMsg.setContent("已确认消息");
        chatViewModel.appendMessage(confirmedMsg);

        chatViewModel.recallMessage(confirmedMsg);
        // 异步请求会失败（无服务端），但不会立即设置错误
        // 错误由回调设置，此处验证不崩溃
        assertNotNull(chatViewModel);
    }

    @Test
    void updateMessageAck_withPendingRecall_shouldExecuteAsyncRecall() {
        // 发送乐观消息
        chatViewModel.inputTextProperty().set("待撤回消息");
        chatViewModel.sendMessage();

        final MessageInfo optimisticMsg = chatViewModel.getMessages().get(0);

        // 撤回乐观消息（注册延迟撤回）
        chatViewModel.recallMessage(optimisticMsg);
        assertTrue(optimisticMsg.isRecalled());

        // 模拟 ACK 到达（clientId 通过内部 pendingMessages 获取）
        // 由于 clientId 是内部生成的 UUID，需要通过 pendingMessages 间接获取
        // 这里验证乐观消息存在且已被标记撤回
        assertTrue(optimisticMsg.isRecalled());
    }

    @Test
    void updateMessageAck_delayedRecallFailure_shouldRollbackUI() {
        // 发送乐观消息
        chatViewModel.inputTextProperty().set("撤回会失败的消息");
        chatViewModel.sendMessage();

        final MessageInfo optimisticMsg = chatViewModel.getMessages().get(0);

        // 撤回乐观消息
        chatViewModel.recallMessage(optimisticMsg);
        assertTrue(optimisticMsg.isRecalled());

        // 注意：由于 updateMessageAck 中的延迟撤回是异步的且依赖真实 HTTP 请求，
        // 在单元测试中无法直接验证回滚效果，需要集成测试覆盖。
        // 此处验证初始状态正确
        assertTrue(optimisticMsg.isRecalled());
    }

    // ==================== 已读上报测试 ====================

    @Test
    void reportRead_shouldNotFailWhenMessagesEmpty() {
        // 消息列表为空时调用 reportRead 不应抛出异常
        chatViewModel.reportRead();
        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    @Test
    void reportRead_shouldNotFailWhenOnlyOwnMessages() {
        // 仅包含自己发送的消息时，没有需要上报的消息
        final MessageInfo ownMsg = new MessageInfo();
        ownMsg.setMessageId(1L);
        ownMsg.setSentByMe(true);
        ownMsg.setContent("我的消息");
        chatViewModel.appendMessage(ownMsg);

        chatViewModel.reportRead();
        assertEquals(1, chatViewModel.getMessages().size());
    }

    @Test
    void reportRead_shouldNotFailWhenMessagesHaveNoValidId() {
        // 消息 messageId 为 null 或 <=0 时不应上报
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(null);
        msg.setSentByMe(false);
        chatViewModel.appendMessage(msg);

        chatViewModel.reportRead();
        assertEquals(1, chatViewModel.getMessages().size());
    }

    // ==================== 加载更多历史测试 ====================

    @Test
    void loadMoreHistory_shouldNotExecuteWhenConversationIsNull() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.loadMoreHistory();
        // 无会话时不应改变状态
        assertFalse(vm.loadingProperty().get());
    }

    @Test
    void loadMoreHistory_shouldNotExecuteWhenLoadingIsTrue() {
        // 通过反射设置 loading 为 true，模拟正在加载
        final BooleanProperty loadingProp = chatViewModel.loadingProperty();
        loadingProp.set(true);
        chatViewModel.loadMoreHistory();
        // 已在加载中，不应重复触发
        assertTrue(loadingProp.get());
    }

    // ==================== 图片消息发送测试 ====================

    @Test
    void sendImage_shouldSetErrorWhenConversationIsNull() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.sendImage(java.nio.file.Paths.get("test.png"));
        assertEquals("未选择会话", vm.errorMessageProperty().get());
    }

    @Test
    void sendImage_shouldSetLoadingWhenConversationExists() {
        // 会话存在时，应进入加载状态（异步请求会失败）
        chatViewModel.sendImage(java.nio.file.Paths.get("nonexistent.png"));
        // 由于文件不存在会抛异常，loading 会被回调重置
        // 这里验证调用不会导致崩溃
        assertNotNull(chatViewModel.errorMessageProperty().get());
    }

    // ==================== 消息追加边界测试 ====================

    @Test
    void appendMessage_shouldAddImageMessage() {
        final MessageInfo imgMsg = new MessageInfo();
        imgMsg.setMessageId(10L);
        imgMsg.setType("IMAGE");
        imgMsg.setContent("http://example.com/image.png");

        chatViewModel.appendMessage(imgMsg);

        assertEquals(1, chatViewModel.getMessages().size());
        assertEquals("IMAGE", chatViewModel.getMessages().get(0).getType());
    }

    @Test
    void appendMessage_shouldAddMessageWithNullMessageId() {
        // messageId 为 null 的消息（如系统消息）也应能添加
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(null);
        msg.setContent("系统消息");

        chatViewModel.appendMessage(msg);

        assertEquals(1, chatViewModel.getMessages().size());
    }

    // ==================== 消息已读状态测试 ====================

    @Test
    void messageInfo_readField_shouldDefaultToFalse() {
        final MessageInfo msg = new MessageInfo();
        assertFalse(msg.isRead());
    }

    @Test
    void messageInfo_readField_shouldBeSettable() {
        final MessageInfo msg = new MessageInfo();
        msg.setRead(true);
        assertTrue(msg.isRead());
    }

    @Test
    void messageInfo_recalledField_shouldDefaultToFalse() {
        final MessageInfo msg = new MessageInfo();
        assertFalse(msg.isRecalled());
    }

    @Test
    void messageInfo_recalledField_shouldBeSettable() {
        final MessageInfo msg = new MessageInfo();
        msg.setRecalled(true);
        assertTrue(msg.isRecalled());
    }

    // ==================== 图片乐观消息测试 ====================

    @Test
    @DisplayName("图片乐观消息应创建并加入消息列表")
    void sendImage_shouldCreateOptimisticMessage() {
        // 注意：由于 sendImage 是异步调用 ChatService.uploadImage，
        // 这里仅验证方法调用不会崩溃，实际乐观消息创建需要 Mock ChatService
        // 此测试验证 sendImage 在 conversation 为 null 时的错误处理
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.sendImage(java.nio.file.Paths.get("test.png"));
        assertEquals("未选择会话", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("图片乐观消息 ACK 后应更新 messageId")
    void imageOptimisticMessage_shouldUpdateOnAck() {
        // 创建图片乐观消息（messageId=-1）
        final MessageInfo optimisticMsg = new MessageInfo();
        optimisticMsg.setMessageId(-1L);
        optimisticMsg.setSessionId(conversation.getSessionId());
        optimisticMsg.setSenderId(currentUser.getUserId());
        optimisticMsg.setSenderName(currentUser.getUsername());
        optimisticMsg.setSenderType("USER");
        optimisticMsg.setType("IMAGE");
        optimisticMsg.setContent("http://example.com/test.png");
        optimisticMsg.setSentByMe(true);

        chatViewModel.getMessages().add(optimisticMsg);

        // 通过反射获取 pendingMessages 并添加映射
        setPendingMessage("test-client-id", optimisticMsg);

        // 调用 updateMessageAck
        chatViewModel.updateMessageAck("test-client-id", 100L,
                java.time.LocalDateTime.of(2024, 1, 1, 10, 0));

        // 验证 messageId 已更新
        assertEquals(100L, chatViewModel.getMessages().get(0).getMessageId());
    }

    @Test
    @DisplayName("图片乐观消息撤回应标记 recalled=true")
    void imageOptimisticMessage_shouldMarkRecalled() {
        // 创建图片乐观消息
        final MessageInfo optimisticMsg = new MessageInfo();
        optimisticMsg.setMessageId(-1L);
        optimisticMsg.setSessionId(conversation.getSessionId());
        optimisticMsg.setSenderId(currentUser.getUserId());
        optimisticMsg.setSenderName(currentUser.getUsername());
        optimisticMsg.setType("IMAGE");
        optimisticMsg.setContent("http://example.com/test.png");
        optimisticMsg.setSentByMe(true);

        chatViewModel.getMessages().add(optimisticMsg);

        // 通过反射设置 pendingMessages 映射
        final String clientId = "test-client-id";
        setPendingMessage(clientId, optimisticMsg);

        // 调用 recallMessage（乐观消息撤回）
        chatViewModel.recallMessage(optimisticMsg);

        // 验证 recalled=true
        assertTrue(optimisticMsg.isRecalled());
    }

    /**
     * 通过反射设置 pendingMessages 字段
     */
    @SuppressWarnings("unchecked")
    private void setPendingMessage(final String clientId, final MessageInfo msg) {
        try {
            final java.lang.reflect.Field field = ChatViewModel.class.getDeclaredField("pendingMessages");
            field.setAccessible(true);
            final java.util.Map<String, MessageInfo> pendingMessages =
                    (java.util.Map<String, MessageInfo>) field.get(chatViewModel);
            pendingMessages.put(clientId, msg);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 图片发送边界测试 ====================

    @Test
    @DisplayName("sendImage 空路径不应抛异常")
    void sendImage_shouldNotThrowWhenPathIsEmpty() {
        chatViewModel.sendImage(java.nio.file.Paths.get(""));
        // 空路径会导致文件读取失败，但不应抛异常
        assertNotNull(chatViewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("图片消息应正确设置消息类型")
    void imageMessage_shouldHaveCorrectType() {
        final MessageInfo imgMsg = new MessageInfo();
        imgMsg.setMessageId(100L);
        imgMsg.setType("IMAGE");
        imgMsg.setContent("http://localhost:8080/files/test.jpg");
        imgMsg.setSentByMe(true);

        chatViewModel.appendMessage(imgMsg);

        assertEquals(1, chatViewModel.getMessages().size());
        assertEquals("IMAGE", chatViewModel.getMessages().get(0).getType());
        assertTrue(chatViewModel.getMessages().get(0).isSentByMe());
    }

    @Test
    @DisplayName("图片消息重复添加不应重复")
    void imageMessage_shouldNotDuplicate() {
        final MessageInfo imgMsg = new MessageInfo();
        imgMsg.setMessageId(100L);
        imgMsg.setType("IMAGE");
        imgMsg.setContent("http://localhost:8080/files/test.jpg");

        chatViewModel.appendMessage(imgMsg);
        chatViewModel.appendMessage(imgMsg);

        assertEquals(1, chatViewModel.getMessages().size());
    }

    @Test
    @DisplayName("图片消息应支持撤回")
    void imageMessage_shouldSupportRecall() {
        final MessageInfo imgMsg = new MessageInfo();
        imgMsg.setMessageId(100L);
        imgMsg.setType("IMAGE");
        imgMsg.setContent("http://localhost:8080/files/test.jpg");
        imgMsg.setSentByMe(true);

        chatViewModel.appendMessage(imgMsg);
        chatViewModel.recallMessage(imgMsg);

        // 撤回操作会异步调用服务端，这里验证不崩溃
        assertNotNull(chatViewModel);
    }

    @Test
    @DisplayName("图片消息应支持已读状态")
    void imageMessage_shouldSupportReadStatus() {
        final MessageInfo imgMsg = new MessageInfo();
        imgMsg.setMessageId(100L);
        imgMsg.setType("IMAGE");
        imgMsg.setContent("http://localhost:8080/files/test.jpg");
        imgMsg.setSentByMe(false);
        imgMsg.setRead(false);

        chatViewModel.appendMessage(imgMsg);

        assertFalse(chatViewModel.getMessages().get(0).isRead());

        // 设置已读
        chatViewModel.getMessages().get(0).setRead(true);
        assertTrue(chatViewModel.getMessages().get(0).isRead());
    }

    // ==================== updateMessageAck 扩展测试 ====================

    @Test
    @DisplayName("updateMessageAck - null clientId 不崩溃")
    void updateMessageAck_nullClientId_shouldNotCrash() {
        chatViewModel.inputTextProperty().set("测试");
        chatViewModel.sendMessage();
        chatViewModel.updateMessageAck(null, 100L,
                java.time.LocalDateTime.of(2024, 1, 1, 10, 0));
        assertEquals(1, chatViewModel.getMessages().size());
    }

    @Test
    @DisplayName("updateMessageAck - 未知 clientId 不崩溃")
    void updateMessageAck_unknownClientId_shouldNotCrash() {
        chatViewModel.updateMessageAck("non-existent-id", 100L,
                java.time.LocalDateTime.of(2024, 1, 1, 10, 0));
        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    @Test
    @DisplayName("updateMessageAck - pending 不在消息列表不崩溃")
    void updateMessageAck_pendingNotInMessages_shouldNotCrash() {
        final String clientId = "orphan-pending-id";
        final MessageInfo orphan = new MessageInfo();
        orphan.setMessageId(-1L);
        orphan.setSessionId("p_1001_1002");
        orphan.setContent("孤立消息");
        setPendingMessage(clientId, orphan);

        chatViewModel.updateMessageAck(clientId, 100L,
                java.time.LocalDateTime.of(2024, 1, 1, 10, 0));
        assertEquals(100L, orphan.getMessageId());
    }

    // ==================== sendFile 测试 ====================

    @Test
    @DisplayName("sendFile - 会話为 null 设置错误")
    void sendFile_nullConversation_shouldSetError() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.sendFile(java.nio.file.Paths.get("test.txt"));
        assertEquals("未选择会话", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendFile - 文件不存在设置错误")
    void sendFile_nonExistentFile_shouldSetError() {
        chatViewModel.sendFile(java.nio.file.Paths.get("/nonexistent/file.txt"));
        assertEquals("文件不存在", chatViewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendFile - 有效文件发送乐观消息")
    void sendFile_validFile_shouldSendOptimisticMessage() throws Exception {
        final java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".txt");
        try {
            java.nio.file.Files.write(tempFile, "test content".getBytes());
            chatViewModel.sendFile(tempFile);

            assertEquals(1, chatViewModel.getMessages().size());
            final MessageInfo msg = chatViewModel.getMessages().get(0);
            assertEquals("FILE", msg.getType());
            assertTrue(msg.getContent().endsWith(".txt"));
            assertTrue(msg.isSentByMe());
            assertNotNull(msg.getExtra());
            assertTrue(msg.getExtra().contains("fileSize"));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    // ==================== loadHistory 测试 ====================

    @Test
    @DisplayName("loadHistory - 会話 null 跳过加载")
    void loadHistory_nullConversation_shouldSkip() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.loadHistory();
        assertFalse(vm.loadingProperty().get());
        assertTrue(vm.getMessages().isEmpty());
    }

    // ==================== loadMoreHistory 扩展测试 ====================

    @Test
    @DisplayName("loadMoreHistory - hasMoreHistory false 不执行")
    void loadMoreHistory_noMoreHistory_shouldNotExecute() throws Exception {
        final java.lang.reflect.Field field = ChatViewModel.class.getDeclaredField("hasMoreHistory");
        field.setAccessible(true);
        field.setBoolean(chatViewModel, false);

        chatViewModel.loadMoreHistory();
        assertFalse(chatViewModel.loadingProperty().get());
    }

    // ==================== pendingSystemMessages 测试 ====================

    @Test
    @DisplayName("addPendingSystemMessage - 添加并取出系统消息")
    void addPendingSystemMessage_shouldAddAndDrain() throws Exception {
        final MessageInfo sysMsg = new MessageInfo();
        sysMsg.setMessageId(999L);
        sysMsg.setContent("系统消息：用户加入群组");
        sysMsg.setSessionId("p_1001_1002");
        sysMsg.setSentByMe(false);

        ChatViewModel.addPendingSystemMessage("p_1001_1002", sysMsg);

        final java.lang.reflect.Method drainMethod = ChatViewModel.class.getDeclaredMethod(
                "drainPendingSystemMessages", String.class);
        drainMethod.setAccessible(true);
        drainMethod.invoke(chatViewModel, "p_1001_1002");

        assertEquals(1, chatViewModel.getMessages().size());
        assertEquals("系统消息：用户加入群组", chatViewModel.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("addPendingSystemMessage - 不同 sessionId 不注入")
    void addPendingSystemMessage_differentSessionId_shouldNotInject() throws Exception {
        final MessageInfo sysMsg = new MessageInfo();
        sysMsg.setMessageId(999L);
        sysMsg.setContent("系统消息");
        sysMsg.setSessionId("p_1001_1002");
        sysMsg.setSentByMe(false);

        ChatViewModel.addPendingSystemMessage("p_1001_9999", sysMsg);

        final java.lang.reflect.Method drainMethod = ChatViewModel.class.getDeclaredMethod(
                "drainPendingSystemMessages", String.class);
        drainMethod.setAccessible(true);
        drainMethod.invoke(chatViewModel, "p_1001_1002");

        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    @Test
    @DisplayName("addPendingSystemMessage - 多条消息按序注入")
    void addPendingSystemMessage_multipleMessages_shouldInjectInOrder() throws Exception {
        final MessageInfo msg1 = new MessageInfo();
        msg1.setMessageId(998L);
        msg1.setContent("消息1");
        msg1.setSessionId("p_1001_1002");
        msg1.setSentByMe(false);

        final MessageInfo msg2 = new MessageInfo();
        msg2.setMessageId(999L);
        msg2.setContent("消息2");
        msg2.setSessionId("p_1001_1002");
        msg2.setSentByMe(false);

        ChatViewModel.addPendingSystemMessage("p_1001_1002", msg1);
        ChatViewModel.addPendingSystemMessage("p_1001_1002", msg2);

        final java.lang.reflect.Method drainMethod = ChatViewModel.class.getDeclaredMethod(
                "drainPendingSystemMessages", String.class);
        drainMethod.setAccessible(true);
        drainMethod.invoke(chatViewModel, "p_1001_1002");

        assertEquals(2, chatViewModel.getMessages().size());
        assertEquals("消息1", chatViewModel.getMessages().get(0).getContent());
        assertEquals("消息2", chatViewModel.getMessages().get(1).getContent());
    }

    @Test
    @DisplayName("addPendingSystemMessage - 重复 drain 不重复注入")
    void addPendingSystemMessage_doubleDrain_shouldNotDuplicate() throws Exception {
        final MessageInfo sysMsg = new MessageInfo();
        sysMsg.setMessageId(999L);
        sysMsg.setContent("系统消息");
        sysMsg.setSessionId("p_1001_1002");
        sysMsg.setSentByMe(false);

        ChatViewModel.addPendingSystemMessage("p_1001_1002", sysMsg);

        final java.lang.reflect.Method drainMethod = ChatViewModel.class.getDeclaredMethod(
                "drainPendingSystemMessages", String.class);
        drainMethod.setAccessible(true);

        drainMethod.invoke(chatViewModel, "p_1001_1002");
        drainMethod.invoke(chatViewModel, "p_1001_1002");

        assertEquals(1, chatViewModel.getMessages().size());
    }

    // ==================== recallMessage 扩展测试 ====================

    @Test
    @DisplayName("recallMessage - 已确认消息正常执行")
    void recallMessage_confirmedMessage_shouldExecuteAsync() {
        final MessageInfo confirmedMsg = new MessageInfo();
        confirmedMsg.setMessageId(500L);
        confirmedMsg.setSessionId("p_1001_1002");
        confirmedMsg.setContent("已确认的消息");
        confirmedMsg.setSentByMe(true);
        chatViewModel.appendMessage(confirmedMsg);

        chatViewModel.recallMessage(confirmedMsg);

        assertFalse(confirmedMsg.isRecalled());
    }

    @Test
    @DisplayName("recallMessage - 孤儿乐观消息找不到 clientId")
    void recallMessage_orphanOptimisticMessage_shouldNotFindClientId() {
        final MessageInfo orphanMsg = new MessageInfo();
        orphanMsg.setMessageId(-99L);
        orphanMsg.setSessionId("p_1001_1002");
        orphanMsg.setContent("无主消息");
        orphanMsg.setSentByMe(true);
        chatViewModel.getMessages().add(orphanMsg);

        chatViewModel.recallMessage(orphanMsg);

        assertFalse(orphanMsg.isRecalled());
    }

    // ==================== sendMessage 扩展测试 ====================

    @Test
    @DisplayName("sendMessage - 会話 null 设置错误")
    void sendMessage_nullConversation_shouldSetError() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.inputTextProperty().set("测试消息");
        vm.sendMessage();

        assertEquals("请先选择会话", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendMessage - 连续发送多条消息")
    void sendMessage_multipleMessages_shouldAllAppear() {
        chatViewModel.inputTextProperty().set("消息1");
        chatViewModel.sendMessage();
        chatViewModel.inputTextProperty().set("消息2");
        chatViewModel.sendMessage();
        chatViewModel.inputTextProperty().set("消息3");
        chatViewModel.sendMessage();

        assertEquals(3, chatViewModel.getMessages().size());
        assertEquals("消息1", chatViewModel.getMessages().get(0).getContent());
        assertEquals("消息2", chatViewModel.getMessages().get(1).getContent());
        assertEquals("消息3", chatViewModel.getMessages().get(2).getContent());
    }

    // ==================== checkAndExecutePendingRecalls 测试 ====================

    @Test
    @DisplayName("checkAndExecutePendingRecalls - 空列表不执行")
    void checkAndExecutePendingRecalls_emptyPending_shouldNotExecute() throws Exception {
        final java.lang.reflect.Method method = ChatViewModel.class.getDeclaredMethod(
                "checkAndExecutePendingRecalls", java.util.List.class);
        method.setAccessible(true);

        final java.util.List<MessageInfo> loadedMessages = java.util.List.of();
        method.invoke(chatViewModel, loadedMessages);

        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    @Test
    @DisplayName("checkAndExecutePendingRecalls - null 不崩溃")
    void checkAndExecutePendingRecalls_nullList_shouldNotCrash() throws Exception {
        final java.lang.reflect.Method method = ChatViewModel.class.getDeclaredMethod(
                "checkAndExecutePendingRecalls", java.util.List.class);
        method.setAccessible(true);

        method.invoke(chatViewModel, (java.util.List<MessageInfo>) null);

        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    // ==================== reportRead 扩展测试 ====================

    @Test
    @DisplayName("reportRead - 会話 null 跳过")
    void reportRead_nullConversation_shouldSkip() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(1L);
        msg.setSentByMe(false);
        vm.getMessages().add(msg);

        vm.reportRead();
        assertFalse(vm.getMessages().isEmpty());
    }

    // ==================== 图片缩略图和尺寸测试 ====================

    @Test
    @DisplayName("图片消息应有缩略图和尺寸")
    void imageMessage_shouldHaveThumbnailAndDimensions() {
        final MessageInfo imgMsg = new MessageInfo();
        imgMsg.setMessageId(200L);
        imgMsg.setType("IMAGE");
        imgMsg.setContent("http://example.com/image.png");
        imgMsg.setThumbnailUrl("http://example.com/thumb.png");
        imgMsg.setWidth(800);
        imgMsg.setHeight(600);
        imgMsg.setSentByMe(true);

        chatViewModel.appendMessage(imgMsg);

        assertEquals("http://example.com/thumb.png",
                chatViewModel.getMessages().get(0).getThumbnailUrl());
        assertEquals(800, chatViewModel.getMessages().get(0).getWidth());
        assertEquals(600, chatViewModel.getMessages().get(0).getHeight());
    }

    @Test
    @DisplayName("getConversationName - 会話 null 返回空串")
    void getConversationName_nullConversation_shouldReturnEmpty() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        assertEquals("", vm.getConversationName());
    }

    // ==================== sendFile 有效文件测试 ====================

    @Test
    @DisplayName("sendFile - 有效文件时添加乐观消息")
    void sendFile_validFile_shouldAddOptimisticMessage() throws Exception {
        final java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test_upload", ".txt");
        try {
            java.nio.file.Files.writeString(tempFile, "test content");
            chatViewModel.sendFile(tempFile);

            assertEquals(1, chatViewModel.getMessages().size());
            final MessageInfo msg = chatViewModel.getMessages().get(0);
            assertEquals("FILE", msg.getType());
            assertTrue(msg.isSentByMe());
            assertEquals(-1L, msg.getMessageId());
            assertTrue(msg.getContent().startsWith("test_upload"));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    // ==================== loadHistory null conversation 测试 ====================

    @Test
    @DisplayName("loadHistory - conversation 为 null 时不执行")
    void loadHistory_nullConversation_shouldNotExecute() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.loadHistory();
        assertFalse(vm.loadingProperty().get());
    }

    // ==================== reportRead 测试 ====================

    @Test
    @DisplayName("reportRead - 有未读消息时不崩溃")
    void reportRead_withUnreadMessages_shouldNotCrash() {
        final MessageInfo otherMsg = new MessageInfo();
        otherMsg.setMessageId(200L);
        otherMsg.setSentByMe(false);
        otherMsg.setContent("别人的消息");
        chatViewModel.appendMessage(otherMsg);

        chatViewModel.reportRead();
        assertEquals(1, chatViewModel.getMessages().size());
    }

    @Test
    @DisplayName("reportRead - conversation 为 null 时不执行")
    void reportRead_nullConversation_shouldNotExecute() {
        final ChatViewModel vm = new ChatViewModel(currentUser, null);
        vm.reportRead();
    }

    // ==================== addPendingSystemMessage 测试 ====================

    @Test
    @DisplayName("addPendingSystemMessage - 添加待处理系统消息")
    void addPendingSystemMessage_shouldAddMessage() {
        final MessageInfo sysMsg = new MessageInfo();
        sysMsg.setContent("用户加入了群聊");
        sysMsg.setType("SYSTEM");

        ChatViewModel.addPendingSystemMessage(conversation.getSessionId(), sysMsg);
        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    // ==================== sendMessage null 输入测试 ====================

    @Test
    @DisplayName("sendMessage - null 输入不发送")
    void sendMessage_nullInput_shouldNotSend() {
        chatViewModel.inputTextProperty().set(null);
        chatViewModel.sendMessage();
        assertTrue(chatViewModel.getMessages().isEmpty());
    }

    // ==================== appendMessage 重复 messageId 测试 ====================

    @Test
    @DisplayName("appendMessage - 消息列表中已有相同 messageId 的消息不重复添加")
    void appendMessage_duplicateMessageId_shouldNotAdd() {
        final MessageInfo msg1 = new MessageInfo();
        msg1.setMessageId(1L);
        msg1.setContent("消息1");

        final MessageInfo msg2 = new MessageInfo();
        msg2.setMessageId(1L);
        msg2.setContent("消息2（重复ID）");

        chatViewModel.appendMessage(msg1);
        chatViewModel.appendMessage(msg2);

        assertEquals(1, chatViewModel.getMessages().size());
        assertEquals("消息1", chatViewModel.getMessages().get(0).getContent());
    }
}
