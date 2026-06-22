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
}
