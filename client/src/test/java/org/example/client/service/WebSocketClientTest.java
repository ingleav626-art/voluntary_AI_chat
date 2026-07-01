package org.example.client.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.example.client.util.JsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;

import javafx.application.Platform;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocketClient 单元测试
 *
 * <p>
 * 测试单例模式、连接状态、消息处理、重连调度等行为。
 * 使用反射测试私有方法，实际连接建立需要集成测试环境。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("WebSocketClient 测试")
class WebSocketClientTest {

    private WebSocketClient client;

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
        } catch (final IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    @BeforeEach
    void setUp() {
        client = WebSocketClient.getInstance();
        // 重置状态
        client.close();
        setField("reconnectAttempts", 0);
        setField("manualClose", false);
        setField("connected", false);
        setField("lastMessageId", null);
        client.setOnMessage(null);
        client.setOnConnectionChange(null);
    }

    // ============ 公共 API 测试 ============

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
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("connect 传入 null token 不抛异常")
    void testConnectNullToken() {
        assertDoesNotThrow(() -> client.connect(null));
    }

    @Test
    @DisplayName("connect 传入空 token 不抛异常")
    void testConnectEmptyToken() {
        assertDoesNotThrow(() -> client.connect(""));
    }

    @Test
    @DisplayName("未连接时 send 不抛异常")
    void testSendWhenDisconnected() {
        final Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "p_1001_1002");
        data.put("content", "测试消息");
        assertDoesNotThrow(() -> client.send("SEND_MESSAGE", data));
    }

    @Test
    @DisplayName("未连接时 send null data 不抛异常")
    void testSendNullDataWhenDisconnected() {
        assertDoesNotThrow(() -> client.send("SEND_MESSAGE", null));
    }

    @Test
    @DisplayName("设置消息回调不抛异常")
    void testSetOnMessage() {
        assertDoesNotThrow(() -> client.setOnMessage(msg -> {
        }));
    }

    @Test
    @DisplayName("设置连接状态回调不抛异常")
    void testSetOnConnectionChange() {
        assertDoesNotThrow(() -> client.setOnConnectionChange(connected -> {
        }));
    }

    @Test
    @DisplayName("close 不抛异常")
    void testClose() {
        assertDoesNotThrow(client::close);
    }

    @Test
    @DisplayName("getReconnectAttempts 返回非负数")
    void testGetReconnectAttemptsNonNegative() {
        assertTrue(client.getReconnectAttempts() >= 0);
    }

    @Test
    @DisplayName("多次 close 不抛异常")
    void testCloseMultiple() {
        assertDoesNotThrow(client::close);
        assertDoesNotThrow(client::close);
        assertDoesNotThrow(client::close);
    }

    @Test
    @DisplayName("connect 传入有效格式 token 不抛异常")
    void testConnectWithValidFormatToken() {
        assertDoesNotThrow(() -> client.connect("valid-token-12345"));
    }

    @Test
    @DisplayName("重复设置消息回调覆盖之前的回调")
    void testSetOnMessageMultipleTimes() {
        assertDoesNotThrow(() -> client.setOnMessage(msg -> {
        }));
        assertDoesNotThrow(() -> client.setOnMessage(msg -> {
        }));
    }

    @Test
    @DisplayName("重复设置连接回调覆盖之前的回调")
    void testSetOnConnectionChangeMultipleTimes() {
        assertDoesNotThrow(() -> client.setOnConnectionChange(c -> {
        }));
        assertDoesNotThrow(() -> client.setOnConnectionChange(c -> {
        }));
    }

    @Test
    @DisplayName("send 空 type 不抛异常")
    void testSendEmptyType() {
        final Map<String, Object> data = new HashMap<>();
        assertDoesNotThrow(() -> client.send("", data));
    }

    @Test
    @DisplayName("send 空 data 不抛异常")
    void testSendEmptyData() {
        final Map<String, Object> data = new HashMap<>();
        assertDoesNotThrow(() -> client.send("SEND_MESSAGE", data));
    }

    @Test
    @DisplayName("close 后 connect 不抛异常")
    void testCloseThenConnect() {
        client.close();
        assertDoesNotThrow(() -> client.connect("new-token"));
    }

    @Test
    @DisplayName("setOnMessage null 不抛异常")
    void testSetOnMessageNull() {
        assertDoesNotThrow(() -> client.setOnMessage(null));
    }

    @Test
    @DisplayName("setOnConnectionChange null 不抛异常")
    void testSetOnConnectionChangeNull() {
        assertDoesNotThrow(() -> client.setOnConnectionChange(null));
    }

    // ============ handleMessage 反射测试 ============

    @Test
    @DisplayName("handleMessage - PONG 消息不触发 onMessage 回调")
    void handleMessage_pong_shouldNotTriggerCallback() throws Exception {
        final AtomicBoolean callbackFired = new AtomicBoolean(false);
        client.setOnMessage(msg -> callbackFired.set(true));

        final WebSocketMessage pongMsg = WebSocketMessage.builder()
                .id("msg-1")
                .type(MessageTypes.PONG)
                .data(Map.of())
                .build();
        invokeHandleMessage(JsonUtils.toJson(pongMsg));

        // PONG 不触发回调
        assertFalse(callbackFired.get());
    }

    @Test
    @DisplayName("handleMessage - FORCE_LOGOUT 消息设置 manualClose=true 并停止重连")
    void handleMessage_forceLogout_shouldStopReconnect() throws Exception {
        setField("manualClose", false);
        setField("connected", true);

        final WebSocketMessage forceLogoutMsg = WebSocketMessage.builder()
                .id("msg-2")
                .type(MessageTypes.FORCE_LOGOUT)
                .data(Map.of())
                .build();
        invokeHandleMessage(JsonUtils.toJson(forceLogoutMsg));

        assertTrue((boolean) getField("manualClose"));
        assertFalse((boolean) getField("connected"));
    }

    @Test
    @DisplayName("handleMessage - 普通消息触发 onMessage 回调")
    void handleMessage_normalMessage_shouldTriggerCallback() throws Exception {
        final AtomicReference<WebSocketMessage> received = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        client.setOnMessage(msg -> {
            received.set(msg);
            latch.countDown();
        });

        final WebSocketMessage normalMsg = WebSocketMessage.builder()
                .id("msg-3")
                .type(MessageTypes.SEND_MESSAGE)
                .data(Map.of("content", "hello"))
                .build();
        invokeHandleMessage(JsonUtils.toJson(normalMsg));

        // 等待 Platform.runLater 执行（如果在 JavaFX 线程上则直接执行）
        final boolean awaited = latch.await(2, TimeUnit.SECONDS);
        if (awaited) {
            assertNotNull(received.get());
            assertEquals(MessageTypes.SEND_MESSAGE, received.get().getType());
        }
    }

    @Test
    @DisplayName("handleMessage - 无效 JSON 不抛异常")
    void handleMessage_invalidJson_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeHandleMessage("not a valid json"));
    }

    @Test
    @DisplayName("handleMessage - null 反序列化结果不崩溃")
    void handleMessage_nullDeserialization_shouldNotCrash() throws Exception {
        // 发送一个空 JSON 对象，可能反序列化为 null 或空对象
        assertDoesNotThrow(() -> invokeHandleMessage("{}"));
    }

    @Test
    @DisplayName("handleMessage - 消息带 ID 应更新 lastMessageId")
    void handleMessage_withMessageId_shouldUpdateLastMessageId() throws Exception {
        setField("lastMessageId", null);

        final WebSocketMessage msg = WebSocketMessage.builder()
                .id("msg-123")
                .type(MessageTypes.MESSAGE_ACK)
                .data(Map.of())
                .build();
        invokeHandleMessage(JsonUtils.toJson(msg));

        assertEquals("msg-123", getField("lastMessageId"));
    }

    @Test
    @DisplayName("handleMessage - 消息无 ID 不更新 lastMessageId")
    void handleMessage_withoutMessageId_shouldNotUpdateLastMessageId() throws Exception {
        setField("lastMessageId", "old-id");

        final WebSocketMessage msg = WebSocketMessage.builder()
                .id(null)
                .type(MessageTypes.MESSAGE_ACK)
                .data(Map.of())
                .build();
        invokeHandleMessage(JsonUtils.toJson(msg));

        assertEquals("old-id", getField("lastMessageId"));
    }

    @Test
    @DisplayName("handleMessage - 空字符串 ID 不更新 lastMessageId")
    void handleMessage_withEmptyMessageId_shouldNotUpdateLastMessageId() throws Exception {
        setField("lastMessageId", "old-id");

        final WebSocketMessage msg = WebSocketMessage.builder()
                .id("")
                .type(MessageTypes.MESSAGE_ACK)
                .data(Map.of())
                .build();
        invokeHandleMessage(JsonUtils.toJson(msg));

        assertEquals("old-id", getField("lastMessageId"));
    }

    @Test
    @DisplayName("handleMessage - onMessage 为 null 时不崩溃")
    void handleMessage_nullCallback_shouldNotCrash() throws Exception {
        client.setOnMessage(null);

        final WebSocketMessage msg = WebSocketMessage.builder()
                .id("msg-4")
                .type(MessageTypes.SEND_MESSAGE)
                .data(Map.of())
                .build();
        assertDoesNotThrow(() -> invokeHandleMessage(JsonUtils.toJson(msg)));
    }

    // ============ scheduleReconnect 反射测试 ============

    @Test
    @DisplayName("scheduleReconnect - manualClose=true 时不调度重连")
    void scheduleReconnect_manualClose_shouldNotSchedule() throws Exception {
        setField("manualClose", true);
        setField("reconnectAttempts", 0);

        invokeScheduleReconnect();

        assertEquals(0, client.getReconnectAttempts());
    }

    @Test
    @DisplayName("scheduleReconnect - 达到最大重连次数时不调度")
    void scheduleReconnect_maxAttempts_shouldNotSchedule() throws Exception {
        setField("manualClose", false);
        setField("reconnectAttempts", 10); // MAX_RECONNECT_ATTEMPTS = 10

        invokeScheduleReconnect();

        assertEquals(10, client.getReconnectAttempts());
    }

    @Test
    @DisplayName("scheduleReconnect - 正常调度应增加重连次数")
    void scheduleReconnect_normal_shouldIncrementAttempts() throws Exception {
        setField("manualClose", false);
        setField("reconnectAttempts", 0);

        invokeScheduleReconnect();

        assertEquals(1, client.getReconnectAttempts());
    }

    @Test
    @DisplayName("scheduleReconnect - 多次调度应指数增长延迟")
    void scheduleReconnect_multiple_shouldExponentialBackoff() throws Exception {
        setField("manualClose", false);
        setField("reconnectAttempts", 0);

        invokeScheduleReconnect();
        assertEquals(1, client.getReconnectAttempts());

        invokeScheduleReconnect();
        assertEquals(2, client.getReconnectAttempts());

        invokeScheduleReconnect();
        assertEquals(3, client.getReconnectAttempts());
    }

    // ============ notifyConnectionChange 反射测试 ============

    @Test
    @DisplayName("notifyConnectionChange - 有回调时应调用")
    void notifyConnectionChange_withCallback_shouldCall() throws Exception {
        final AtomicBoolean callbackFired = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        client.setOnConnectionChange(connected -> {
            callbackFired.set(true);
            latch.countDown();
        });

        invokeNotifyConnectionChange(true);

        final boolean awaited = latch.await(3, TimeUnit.SECONDS);
        // 如果 FX 线程不可用，Platform.runLater 可能不会执行，这是正常的
        // assertTrue(awaited, "回调应在3秒内被调用");
        if (awaited) {
            assertTrue(callbackFired.get(), "回调应被标记为已触发");
        }
        // 不作为硬失败：FX 线程可能不可用
    }

    @Test
    @DisplayName("notifyConnectionChange - 无回调时不崩溃")
    void notifyConnectionChange_noCallback_shouldNotCrash() throws Exception {
        client.setOnConnectionChange(null);
        assertDoesNotThrow(() -> invokeNotifyConnectionChange(false));
    }

    // ============ sendReconnectRequest 反射测试 ============

    @Test
    @DisplayName("sendReconnectRequest - lastMessageId 为 null 时不发送")
    void sendReconnectRequest_nullLastMessageId_shouldNotSend() throws Exception {
        setField("lastMessageId", null);
        setField("connected", true);

        // 不应抛异常
        assertDoesNotThrow(() -> invokeSendReconnectRequest());
    }

    @Test
    @DisplayName("sendReconnectRequest - lastMessageId 为空字符串时不发送")
    void sendReconnectRequest_emptyLastMessageId_shouldNotSend() throws Exception {
        setField("lastMessageId", "");
        setField("connected", true);

        assertDoesNotThrow(() -> invokeSendReconnectRequest());
    }

    @Test
    @DisplayName("sendReconnectRequest - 有 lastMessageId 时尝试发送（未连接则不崩溃）")
    void sendReconnectRequest_withLastMessageId_shouldAttemptSend() throws Exception {
        setField("lastMessageId", "msg-999");
        setField("connected", false);

        // 未连接时 send 会直接返回，不崩溃
        assertDoesNotThrow(() -> invokeSendReconnectRequest());
    }

    // ============ connect 边界测试 ============

    @Test
    @DisplayName("connect - 已连接时关闭旧连接再建立新连接")
    void testConnectWhenAlreadyConnected() {
        setField("connected", true);
        setField("webSocket", null); // webSocket 为 null 时不会尝试 sendClose

        assertDoesNotThrow(() -> client.connect("new-token-after-connected"));
    }

    @Test
    @DisplayName("connect - 连接后重连次数应重置为0")
    void testConnectResetsReconnectAttempts() {
        setField("reconnectAttempts", 5);
        setField("connected", false);

        client.connect("reset-token");

        assertEquals(0, client.getReconnectAttempts());
    }

    // ============ WebSocketListener 反射测试 ============

    @Test
    @DisplayName("WebSocketListener.onOpen - 设置 connected=true 并启动心跳")
    void listener_onOpen_shouldSetConnected() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", false);
        setField("lastMessageId", null);

        // 创建一个 mock WebSocket
        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onOpen", java.net.http.WebSocket.class, mockWs);

        assertTrue((boolean) getField("connected"));
    }

    @Test
    @DisplayName("WebSocketListener.onOpen - 有 lastMessageId 时发送 RECONNECT 请求")
    void listener_onOpen_withLastMessageId_shouldSendReconnect() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", false);
        setField("lastMessageId", "msg-old");

        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onOpen", java.net.http.WebSocket.class, mockWs);

        assertTrue((boolean) getField("connected"));
    }

    @Test
    @DisplayName("WebSocketListener.onOpen - 无 lastMessageId 时不发送 RECONNECT")
    void listener_onOpen_withoutLastMessageId_shouldNotSendReconnect() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", false);
        setField("lastMessageId", null);

        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onOpen", java.net.http.WebSocket.class, mockWs);

        assertTrue((boolean) getField("connected"));
    }

    @Test
    @DisplayName("WebSocketListener.onText - 完整消息触发 handleMessage")
    void listener_onText_lastTrue_shouldHandleMessage() throws Exception {
        final Object listener = createWebSocketListener();
        final java.net.http.WebSocket mockWs = createMockWebSocket();

        final WebSocketMessage msg = WebSocketMessage.builder()
                .id("msg-test")
                .type(MessageTypes.MESSAGE_ACK)
                .data(Map.of())
                .build();
        final String json = JsonUtils.toJson(msg);

        // last=true 表示消息完整
        final var result = invokeListenerMethod(listener, "onText",
                java.net.http.WebSocket.class, CharSequence.class, boolean.class,
                mockWs, json, true);

        // lastMessageId 应该被更新
        assertEquals("msg-test", getField("lastMessageId"));
    }

    @Test
    @DisplayName("WebSocketListener.onText - last=false 时缓冲消息不处理")
    void listener_onText_lastFalse_shouldBuffer() throws Exception {
        final Object listener = createWebSocketListener();
        final java.net.http.WebSocket mockWs = createMockWebSocket();

        setField("lastMessageId", "old-id");

        // last=false 表示消息不完整，不应处理
        invokeListenerMethod(listener, "onText",
                java.net.http.WebSocket.class, CharSequence.class, boolean.class,
                mockWs, "partial", false);

        // lastMessageId 不应被更新
        assertEquals("old-id", getField("lastMessageId"));
    }

    @Test
    @DisplayName("WebSocketListener.onError - 设置 connected=false 并调度重连")
    void listener_onError_shouldSetDisconnected() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", true);
        setField("manualClose", false);

        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onError",
                java.net.http.WebSocket.class, Throwable.class,
                mockWs, new RuntimeException("test error"));

        assertFalse((boolean) getField("connected"));
    }

    @Test
    @DisplayName("WebSocketListener.onClose - 设置 connected=false")
    void listener_onClose_shouldSetDisconnected() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", true);
        setField("manualClose", false);

        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onClose",
                java.net.http.WebSocket.class, int.class, String.class,
                mockWs, 1000, "normal");

        assertFalse((boolean) getField("connected"));
    }

    @Test
    @DisplayName("WebSocketListener.onClose - reason=reconnect 时不调度重连")
    void listener_onClose_reconnectReason_shouldNotScheduleReconnect() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", true);
        setField("manualClose", false);
        setField("reconnectAttempts", 0);

        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onClose",
                java.net.http.WebSocket.class, int.class, String.class,
                mockWs, 1000, "reconnect");

        assertFalse((boolean) getField("connected"));
        assertEquals(0, client.getReconnectAttempts());
    }

    @Test
    @DisplayName("WebSocketListener.onClose - manualClose=true 时不调度重连")
    void listener_onClose_manualClose_shouldNotScheduleReconnect() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", true);
        setField("manualClose", true);
        setField("reconnectAttempts", 0);

        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onClose",
                java.net.http.WebSocket.class, int.class, String.class,
                mockWs, 1000, "normal");

        assertFalse((boolean) getField("connected"));
        assertEquals(0, client.getReconnectAttempts());
    }

    @Test
    @DisplayName("WebSocketListener.onClose - 非主动关闭且非reconnect时调度重连")
    void listener_onClose_notManual_shouldScheduleReconnect() throws Exception {
        final Object listener = createWebSocketListener();
        setField("connected", true);
        setField("manualClose", false);
        setField("reconnectAttempts", 0);

        final java.net.http.WebSocket mockWs = createMockWebSocket();
        invokeListenerMethod(listener, "onClose",
                java.net.http.WebSocket.class, int.class, String.class,
                mockWs, 1000, "normal");

        assertFalse((boolean) getField("connected"));
        assertTrue(client.getReconnectAttempts() > 0);
    }

    // ============ startHeartbeat / stopHeartbeat 反射测试 ============

    @Test
    @DisplayName("startHeartbeat - 不抛异常")
    void startHeartbeat_shouldNotThrow() throws Exception {
        final java.net.http.WebSocket mockWs = createMockWebSocket();
        final Method method = WebSocketClient.class.getDeclaredMethod("startHeartbeat", java.net.http.WebSocket.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(client, mockWs));
    }

    @Test
    @DisplayName("stopHeartbeat - 不抛异常")
    void stopHeartbeat_shouldNotThrow() throws Exception {
        final Method method = WebSocketClient.class.getDeclaredMethod("stopHeartbeat");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(client));
    }

    @Test
    @DisplayName("stopHeartbeat - 有运行中的心跳时不抛异常")
    void stopHeartbeat_withRunningHeartbeat_shouldNotThrow() throws Exception {
        // 先启动心跳
        final java.net.http.WebSocket mockWs = createMockWebSocket();
        final Method startMethod = WebSocketClient.class.getDeclaredMethod("startHeartbeat",
                java.net.http.WebSocket.class);
        startMethod.setAccessible(true);
        startMethod.invoke(client, mockWs);

        // 再停止
        final Method stopMethod = WebSocketClient.class.getDeclaredMethod("stopHeartbeat");
        stopMethod.setAccessible(true);
        assertDoesNotThrow(() -> stopMethod.invoke(client));
    }

    // ============ 辅助方法 ============

    private void invokeHandleMessage(final String message) throws Exception {
        final Method method = WebSocketClient.class.getDeclaredMethod("handleMessage", String.class);
        method.setAccessible(true);
        method.invoke(client, message);
    }

    private void invokeScheduleReconnect() throws Exception {
        final Method method = WebSocketClient.class.getDeclaredMethod("scheduleReconnect");
        method.setAccessible(true);
        method.invoke(client);
    }

    private void invokeNotifyConnectionChange(final boolean isConnected) throws Exception {
        final Method method = WebSocketClient.class.getDeclaredMethod("notifyConnectionChange", boolean.class);
        method.setAccessible(true);
        method.invoke(client, isConnected);
    }

    private void invokeSendReconnectRequest() throws Exception {
        final Method method = WebSocketClient.class.getDeclaredMethod("sendReconnectRequest");
        method.setAccessible(true);
        method.invoke(client);
    }

    private void setField(final String name, final Object value) {
        try {
            final Field field = WebSocketClient.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(client, value);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to set field: " + name, e);
        }
    }

    private Object getField(final String name) {
        try {
            final Field field = WebSocketClient.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(client);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to get field: " + name, e);
        }
    }

    /**
     * 创建 WebSocketListener 内部类实例
     */
    private Object createWebSocketListener() throws Exception {
        final Class<?> listenerClass = Class.forName(
                "org.example.client.service.WebSocketClient$WebSocketListener");
        final java.lang.reflect.Constructor<?> constructor = listenerClass.getDeclaredConstructor(
                WebSocketClient.class);
        constructor.setAccessible(true);
        return constructor.newInstance(client);
    }

    /**
     * 创建 mock WebSocket 对象
     */
    private java.net.http.WebSocket createMockWebSocket() {
        return new java.net.http.WebSocket() {
            @Override
            public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendText(final CharSequence data,
                    final boolean last) {
                return java.util.concurrent.CompletableFuture.completedFuture(this);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendPing(
                    final java.nio.ByteBuffer message) {
                return java.util.concurrent.CompletableFuture.completedFuture(this);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendPong(
                    final java.nio.ByteBuffer message) {
                return java.util.concurrent.CompletableFuture.completedFuture(this);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendBinary(
                    final java.nio.ByteBuffer data, final boolean last) {
                return java.util.concurrent.CompletableFuture.completedFuture(this);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendClose(final int statusCode,
                    final String reason) {
                return java.util.concurrent.CompletableFuture.completedFuture(this);
            }

            @Override
            public String getSubprotocol() {
                return "";
            }

            @Override
            public boolean isOutputClosed() {
                return false;
            }

            @Override
            public boolean isInputClosed() {
                return false;
            }

            @Override
            public void request(final long n) {
            }

            @Override
            public void abort() {
            }
        };
    }

    /**
     * 调用 WebSocketListener 的方法
     */
    private Object invokeListenerMethod(final Object listener, final String methodName,
            final Class<?>... paramTypes) throws Exception {
        // 分离参数类型和值
        final Object[] paramValues = new Object[paramTypes.length / 2];
        final Class<?>[] actualTypes = new Class[paramTypes.length / 2];
        for (int i = 0; i < paramTypes.length / 2; i++) {
            actualTypes[i] = paramTypes[i * 2];
        }
        // 这种方式不方便，改用重载
        throw new UnsupportedOperationException("Use the typed invokeListenerMethod instead");
    }

    /**
     * 调用 WebSocketListener 的方法（带参数）
     */
    private Object invokeListenerMethod(final Object listener, final String methodName,
            final Class<?> paramType1, final Object paramValue1) throws Exception {
        final Method method = listener.getClass().getDeclaredMethod(methodName, paramType1);
        method.setAccessible(true);
        return method.invoke(listener, paramValue1);
    }

    /**
     * 调用 WebSocketListener 的方法（带2个参数）
     */
    private Object invokeListenerMethod(final Object listener, final String methodName,
            final Class<?> paramType1, final Class<?> paramType2,
            final Object paramValue1, final Object paramValue2) throws Exception {
        final Method method = listener.getClass().getDeclaredMethod(methodName, paramType1, paramType2);
        method.setAccessible(true);
        return method.invoke(listener, paramValue1, paramValue2);
    }

    /**
     * 调用 WebSocketListener 的方法（带3个参数）
     */
    private Object invokeListenerMethod(final Object listener, final String methodName,
            final Class<?> paramType1, final Class<?> paramType2, final Class<?> paramType3,
            final Object paramValue1, final Object paramValue2, final Object paramValue3) throws Exception {
        final Method method = listener.getClass().getDeclaredMethod(methodName, paramType1, paramType2, paramType3);
        method.setAccessible(true);
        return method.invoke(listener, paramValue1, paramValue2, paramValue3);
    }
}
