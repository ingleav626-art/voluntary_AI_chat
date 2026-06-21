package org.example.client.service;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.ConversationInfo;
import org.example.client.model.MarkReadRequest;
import org.example.client.model.MessageInfo;
import org.example.client.model.PageResult;
import org.example.client.model.SendMessageRequest;
import org.example.client.model.SendMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatService 单元测试
 *
 * <p>测试单例模式、方法返回值非空、参数边界等行为。
 * HTTP 请求部分需要集成测试或 Mock。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ChatService 测试")
class ChatServiceTest {

    @Test
    @DisplayName("获取单例实例 - 同一实例")
    void testGetInstance() {
        final ChatService instance1 = ChatService.getInstance();
        final ChatService instance2 = ChatService.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "单例应返回同一实例");
    }

    @Test
    @DisplayName("getConversations(分页) 返回非空 CompletableFuture")
    void testGetConversationsPagedReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> future =
                chatService.getConversations(1, 20);

        assertNotNull(future, "getConversations 应返回非空 Future");
    }

    @Test
    @DisplayName("getConversations(无参) 默认分页返回非空 Future")
    void testGetConversationsDefaultReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> future =
                chatService.getConversations();

        assertNotNull(future, "getConversations 默认分页应返回非空 Future");
    }

    @Test
    @DisplayName("getHistory 返回非空 CompletableFuture")
    void testGetHistoryReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<PageResult<MessageInfo>>> future =
                chatService.getHistory("p_1001_1002", 1, 20);

        assertNotNull(future, "getHistory 应返回非空 Future");
    }

    @Test
    @DisplayName("getHistory null sessionId 不抛异常")
    void testGetHistoryNullSessionId() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.getHistory(null, 1, 20));
    }

    @Test
    @DisplayName("sendMessage 返回非空 CompletableFuture")
    void testSendMessageReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final SendMessageRequest request = new SendMessageRequest("p_1001_1002", "TEXT", "你好");

        final CompletableFuture<ApiResponse<SendMessageResponse>> future =
                chatService.sendMessage(request);

        assertNotNull(future, "sendMessage 应返回非空 Future");
    }

    @Test
    @DisplayName("markRead 正常参数返回非空 Future")
    void testMarkReadNormal() {
        final ChatService chatService = ChatService.getInstance();
        final MarkReadRequest request = new MarkReadRequest(
                "p_1001_1002", Arrays.asList(1L, 2L, 3L));

        final CompletableFuture<ApiResponse<Void>> future = chatService.markRead(request);

        assertNotNull(future, "markRead 应返回非空 Future");
    }

    @Test
    @DisplayName("markRead null 消息列表不抛异常")
    void testMarkReadNullMessageIds() {
        final ChatService chatService = ChatService.getInstance();
        final MarkReadRequest request = new MarkReadRequest("p_1001_1002", null);

        assertDoesNotThrow(() -> chatService.markRead(request));
    }

    @Test
    @DisplayName("recallMessage 返回非空 CompletableFuture")
    void testRecallMessageReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<Void>> future =
                chatService.recallMessage(1001L);

        assertNotNull(future, "recallMessage 应返回非空 Future");
    }

    @Test
    @DisplayName("recallMessage null ID 不抛异常")
    void testRecallMessageNullId() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.recallMessage(null));
    }
}
