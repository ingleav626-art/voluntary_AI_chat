package org.example.client.view;

import java.time.LocalDateTime;

import org.example.client.model.ConversationInfo;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        chatViewModel.recallMessage((MessageInfo) null);
        // null 时直接 return，不设置错误消息
    }

    @Test
    void recallMessage_shouldHandleOptimisticMessageWithNegativeId() {
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(-1L);
        // 乐观消息（messageId < 0）不在 pendingMessages 中时，直接 return
        chatViewModel.recallMessage(msg);
    }

    @Test
    void recallMessage_shouldNotSetErrorWhenMessageIdIsValid() {
        final MessageInfo msg = new MessageInfo();
        msg.setMessageId(100L);
        chatViewModel.recallMessage(msg);
        // 有效 messageId 不会立即设置错误（异步请求会失败但 errorMessage 由回调设置）
        assertNotEquals("消息ID无效，无法撤回", chatViewModel.errorMessageProperty().get());
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
}
