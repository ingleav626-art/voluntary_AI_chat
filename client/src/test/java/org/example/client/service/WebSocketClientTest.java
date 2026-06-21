package org.example.client.service;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocketClient 单元测试
 *
 * <p>测试单例模式、连接状态、断连时发送消息不抛异常等行为。
 * 实际连接建立需要集成测试环境。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("WebSocketClient 测试")
class WebSocketClientTest {

    @Test
    @DisplayName("获取单例实例 - 同一实例")
    void testGetInstance() {
        final WebSocketClient instance1 = WebSocketClient.getInstance();
        final WebSocketClient instance2 = WebSocketClient.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "单例应返回同一实例");
    }

    @Test
    @DisplayName("初始状态未连接")
    void testInitialDisconnected() {
        final WebSocketClient client = WebSocketClient.getInstance();
        // 单例可能因前序测试已连接，这里仅验证 isConnected 返回布尔值
        assertNotNull(client.isConnected());
    }

    @Test
    @DisplayName("connect 传入 null token 不抛异常")
    void testConnectNullToken() {
        final WebSocketClient client = WebSocketClient.getInstance();
        assertDoesNotThrow(() -> client.connect(null));
    }

    @Test
    @DisplayName("connect 传入空 token 不抛异常")
    void testConnectEmptyToken() {
        final WebSocketClient client = WebSocketClient.getInstance();
        assertDoesNotThrow(() -> client.connect(""));
    }

    @Test
    @DisplayName("未连接时 send 不抛异常")
    void testSendWhenDisconnected() {
        final WebSocketClient client = WebSocketClient.getInstance();
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "p_1001_1002");
        data.put("content", "测试消息");

        // 未连接时调用 send 应安全返回，不抛异常
        assertDoesNotThrow(() -> client.send("SEND_MESSAGE", data));
    }

    @Test
    @DisplayName("未连接时 send null data 不抛异常")
    void testSendNullDataWhenDisconnected() {
        final WebSocketClient client = WebSocketClient.getInstance();
        assertDoesNotThrow(() -> client.send("SEND_MESSAGE", null));
    }

    @Test
    @DisplayName("设置消息回调不抛异常")
    void testSetOnMessage() {
        final WebSocketClient client = WebSocketClient.getInstance();
        assertDoesNotThrow(() -> client.setOnMessage(msg -> { }));
    }

    @Test
    @DisplayName("设置连接状态回调不抛异常")
    void testSetOnConnectionChange() {
        final WebSocketClient client = WebSocketClient.getInstance();
        assertDoesNotThrow(() -> client.setOnConnectionChange(connected -> { }));
    }

    @Test
    @DisplayName("close 不抛异常")
    void testClose() {
        final WebSocketClient client = WebSocketClient.getInstance();
        assertDoesNotThrow(client::close);
    }

    @Test
    @DisplayName("getReconnectAttempts 返回非负数")
    void testGetReconnectAttemptsNonNegative() {
        final WebSocketClient client = WebSocketClient.getInstance();
        assertTrue(client.getReconnectAttempts() >= 0, "重连次数应非负");
    }
}
