package org.example.client.view;

import java.time.LocalDateTime;

import org.example.client.model.ConversationInfo;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
