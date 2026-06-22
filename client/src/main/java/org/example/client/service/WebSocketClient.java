package org.example.client.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.example.client.config.ClientConfig;
import org.example.client.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;

import javafx.application.Platform;

/**
 * WebSocket 客户端
 *
 * <p>
 * 负责与服务端建立 WebSocket 连接，处理实时消息收发、心跳维持和断线重连。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class WebSocketClient {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketClient.class);

    private static final WebSocketClient INSTANCE = new WebSocketClient();

    /** WebSocket 连接路径 */
    private static final String WS_PATH = "/ws?token=";

    /** 心跳间隔（秒） */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    /** 初始重连延迟（秒） */
    private static final long INITIAL_RECONNECT_DELAY_SECONDS = 1;

    /** 最大重连延迟（秒） */
    private static final long MAX_RECONNECT_DELAY_SECONDS = 60;

    /** 最大重连次数 */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    /** WebSocket 连接对象 */
    private java.net.http.WebSocket webSocket;

    /** 心跳定时器 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "ws-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /** 心跳任务句柄 */
    private ScheduledFuture<?> heartbeatTask;

    /** 重连任务句柄 */
    private ScheduledFuture<?> reconnectTask;

    /** 当前 token */
    private String currentToken;

    /** 重连次数 */
    private int reconnectAttempts;

    /** 连接状态 */
    private volatile boolean connected;

    /** 是否主动关闭 */
    private volatile boolean manualClose;

    /** 最后收到的服务端消息ID，用于断线重连补发 */
    private volatile String lastMessageId;

    /** 消息回调 */
    private Consumer<WebSocketMessage> onMessage;

    /** 连接状态回调 */
    private Consumer<Boolean> onConnectionChange;

    private WebSocketClient() {
        // 单例模式，禁止外部实例化
    }

    public static WebSocketClient getInstance() {
        return INSTANCE;
    }

    /**
     * 设置消息回调
     *
     * @param callback 消息回调
     */
    public void setOnMessage(final Consumer<WebSocketMessage> callback) {
        this.onMessage = callback;
    }

    /**
     * 设置连接状态回调
     *
     * @param callback 连接状态回调
     */
    public void setOnConnectionChange(final Consumer<Boolean> callback) {
        this.onConnectionChange = callback;
    }

    /**
     * 建立 WebSocket 连接
     *
     * @param token JWT Token
     */
    public void connect(final String token) {
        if (token == null || token.isEmpty()) {
            LOG.error("Token 为空，无法建立 WebSocket 连接");
            return;
        }

        // 先关闭旧连接，防止被服务端踢掉后触发重连循环
        if (webSocket != null && connected) {
            LOG.info("关闭旧连接，准备建立新连接");
            manualClose = true;
            try {
                webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "reconnect");
            } catch (final Exception e) {
                LOG.warn("关闭旧连接失败", e);
            }
            connected = false;
        }

        this.currentToken = token;
        this.manualClose = false;
        this.reconnectAttempts = 0;

        doConnect();
    }

    /**
     * 执行连接
     */
    private void doConnect() {
        final String baseUrl = ClientConfig.getInstance().getBaseUrl();
        // 将 http(s):// 转为 ws(s)://
        final String wsBaseUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://");
        // 去掉 /api 后缀
        final String host = wsBaseUrl.substring(0, wsBaseUrl.length() - "/api".length());
        final String wsUrl = host + WS_PATH + currentToken;

        LOG.info("建立 WebSocket 连接");

        try {
            final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(ClientConfig.getInstance().getConnectTimeout()))
                    .build();

            final CompletableFuture<java.net.http.WebSocket> future = client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(ClientConfig.getInstance().getConnectTimeout()))
                    .buildAsync(URI.create(wsUrl), new WebSocketListener());

            future.thenAccept(ws -> {
                WebSocketClient.this.webSocket = ws;
                LOG.info("WebSocket 连接已建立");
            }).exceptionally(ex -> {
                LOG.error("WebSocket 连接失败", ex);
                connected = false;
                notifyConnectionChange(false);
                scheduleReconnect();
                return null;
            });
        } catch (final Exception e) {
            LOG.error("WebSocket 连接异常", e);
            scheduleReconnect();
        }
    }

    /**
     * WebSocket 监听器
     */
    private final class WebSocketListener implements java.net.http.WebSocket.Listener {

        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(final java.net.http.WebSocket webSocket) {
            LOG.info("WebSocket 连接已建立");
            connected = true;
            reconnectAttempts = 0;
            startHeartbeat(webSocket);
            notifyConnectionChange(true);

            // 断线重连后发送 RECONNECT 请求，拉取离线期间的消息
            if (lastMessageId != null && !lastMessageId.isEmpty()) {
                sendReconnectRequest();
            }

            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(final java.net.http.WebSocket webSocket,
                final CharSequence data, final boolean last) {
            messageBuffer.append(data);
            if (last) {
                final String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(final java.net.http.WebSocket webSocket, final Throwable error) {
            LOG.error("WebSocket 错误", error);
            connected = false;
            notifyConnectionChange(false);
            scheduleReconnect();
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(final java.net.http.WebSocket webSocket,
                final int statusCode, final String reason) {
            LOG.info("WebSocket 连接关闭: statusCode={}, reason={}", statusCode, reason);
            connected = false;
            stopHeartbeat();
            notifyConnectionChange(false);
            // 如果是主动重连（reason=reconnect），不触发自动重连逻辑
            if (!manualClose && !"reconnect".equals(reason)) {
                scheduleReconnect();
            }
            return null;
        }
    }

    /**
     * 处理接收到的消息
     *
     * @param message 消息内容
     */
    private void handleMessage(final String message) {
        try {
            final WebSocketMessage wsMessage = JsonUtils.fromJson(message, WebSocketMessage.class);
            if (wsMessage == null) {
                LOG.warn("消息解析失败: {}", message);
                return;
            }

            // PONG 消息不触发回调，仅用于心跳确认
            if (MessageTypes.PONG.equals(wsMessage.getType())) {
                LOG.debug("收到 PONG 心跳响应");
                return;
            }

            // FORCE_LOGOUT 消息表示被踢下线，停止重连
            if (MessageTypes.FORCE_LOGOUT.equals(wsMessage.getType())) {
                LOG.warn("收到强制下线通知，停止重连");
                manualClose = true;
                connected = false;
                stopHeartbeat();
                if (reconnectTask != null && !reconnectTask.isDone()) {
                    reconnectTask.cancel(false);
                }
            }

            // 记录最后收到的服务端消息ID，用于断线重连补发
            if (wsMessage.getId() != null && !wsMessage.getId().isEmpty()) {
                lastMessageId = wsMessage.getId();
            }

            LOG.debug("收到消息: type={}", wsMessage.getType());

            if (onMessage != null) {
                Platform.runLater(() -> onMessage.accept(wsMessage));
            }
        } catch (final Exception e) {
            LOG.error("处理消息异常: {}", message, e);
        }
    }

    /**
     * 发送消息
     *
     * @param type 消息类型
     * @param data 消息数据
     */
    public void send(final String type, final Map<String, Object> data) {
        if (!connected || webSocket == null) {
            LOG.warn("WebSocket 未连接，消息发送失败: type={}", type);
            return;
        }

        final WebSocketMessage message = WebSocketMessage.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(data)
                .build();

        final String json = JsonUtils.toJson(message);
        if (json == null) {
            LOG.error("消息序列化失败: type={}", type);
            return;
        }

        try {
            webSocket.sendText(json, true);
            LOG.debug("发送消息: type={}", type);
        } catch (final Exception e) {
            LOG.error("发送消息失败: type={}", type, e);
        }
    }

    /**
     * 发送断线重连请求
     * 携带最后收到的消息ID，服务端返回离线期间的消息
     */
    private void sendReconnectRequest() {
        if (lastMessageId == null || lastMessageId.isEmpty()) {
            return;
        }

        final Map<String, Object> data = new HashMap<>();
        data.put("lastMessageId", lastMessageId);
        send(MessageTypes.RECONNECT, data);
        LOG.info("发送断线重连请求: lastMessageId={}", lastMessageId);
    }

    /**
     * 启动心跳
     *
     * @param ws WebSocket 连接
     */
    private void startHeartbeat(final java.net.http.WebSocket ws) {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                final WebSocketMessage ping = WebSocketMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .type(MessageTypes.PING)
                        .data(Map.of())
                        .build();
                final String json = JsonUtils.toJson(ping);
                if (json != null) {
                    ws.sendText(json, true);
                    LOG.debug("发送 PING 心跳");
                }
            } catch (final Exception e) {
                LOG.error("心跳发送失败", e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isDone()) {
            heartbeatTask.cancel(false);
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        if (manualClose) {
            LOG.info("主动关闭，不进行重连");
            return;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOG.error("达到最大重连次数 {}，停止重连", MAX_RECONNECT_ATTEMPTS);
            return;
        }

        reconnectAttempts++;
        // 指数退避
        final long delay = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (1L << (reconnectAttempts - 1)),
                MAX_RECONNECT_DELAY_SECONDS);

        LOG.info("计划第 {} 次重连，{} 秒后执行", reconnectAttempts, delay);

        if (reconnectTask != null && !reconnectTask.isDone()) {
            reconnectTask.cancel(false);
        }

        reconnectTask = scheduler.schedule(() -> {
            LOG.info("执行第 {} 次重连", reconnectAttempts);
            doConnect();
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * 通知连接状态变更
     *
     * @param isConnected 是否已连接
     */
    private void notifyConnectionChange(final boolean isConnected) {
        if (onConnectionChange != null) {
            Platform.runLater(() -> onConnectionChange.accept(isConnected));
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        manualClose = true;
        connected = false;
        stopHeartbeat();
        if (reconnectTask != null && !reconnectTask.isDone()) {
            reconnectTask.cancel(false);
        }
        if (webSocket != null) {
            try {
                webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "client close");
            } catch (final Exception e) {
                LOG.error("关闭 WebSocket 异常", e);
            }
        }
        LOG.info("WebSocket 连接已关闭");
    }

    /**
     * 是否已连接
     *
     * @return 连接状态
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 获取当前重连次数
     *
     * @return 重连次数
     */
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }
}
