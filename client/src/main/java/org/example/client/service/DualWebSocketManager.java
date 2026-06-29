package org.example.client.service;

import java.util.Map;
import java.util.function.Consumer;

import org.example.client.config.ClientConfig;
import org.example.client.config.ServerConnectionManager;
import org.example.client.config.ServerMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voluntary.chat.common.model.WebSocketMessage;

/**
 * 双WebSocket连接管理器
 *
 * <p>
 * 管理本地和云端两个WebSocket连接：
 * <ul>
 * <li>本地连接：用于AI聊天、向量检索等隐私数据</li>
 * <li>云端连接：用于真人聊天、群聊等多人协作</li>
 * </ul>
 * </p>
 *
 * <p>
 * 消息路由规则：
 * <ul>
 * <li>AI_* 消息类型：使用本地连接</li>
 * <li>VECTOR_* 消息类型：使用本地连接</li>
 * <li>其他消息类型：使用云端连接</li>
 * </ul>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class DualWebSocketManager {

    private static final Logger LOG = LoggerFactory.getLogger(DualWebSocketManager.class);

    private static final DualWebSocketManager INSTANCE = new DualWebSocketManager();

    /** 本地连接 */
    private final WebSocketConnection localConnection;

    /** 云端连接 */
    private final WebSocketConnection cloudConnection;

    /** 消息回调 */
    private Consumer<WebSocketMessage> onMessage;

    /** 连接状态回调 */
    private Consumer<Boolean> onConnectionChange;

    private DualWebSocketManager() {
        this.localConnection = new WebSocketConnection("本地");
        this.cloudConnection = new WebSocketConnection("云端");
    }

    /**
     * 获取单例实例
     */
    public static DualWebSocketManager getInstance() {
        return INSTANCE;
    }

    /**
     * 设置消息回调
     *
     * @param callback 消息回调
     */
    public void setOnMessage(final Consumer<WebSocketMessage> callback) {
        this.onMessage = callback;
        localConnection.setOnMessage(callback);
        cloudConnection.setOnMessage(callback);
    }

    /**
     * 设置连接状态回调
     *
     * @param callback 连接状态回调
     */
    public void setOnConnectionChange(final Consumer<Boolean> callback) {
        this.onConnectionChange = callback;
        localConnection.setOnConnectionChange(callback);
        cloudConnection.setOnConnectionChange(callback);
    }

    /**
     * 建立双WebSocket连接
     *
     * @param token JWT Token
     */
    public void connect(final String token) {
        if (token == null || token.isEmpty()) {
            LOG.error("Token 为空，无法建立 WebSocket 连接");
            return;
        }

        final ServerConnectionManager connectionManager = ServerConnectionManager.getInstance();
        final ServerMode mode = connectionManager.getCurrentMode();
        final String localUrl = ClientConfig.getInstance().getLocalBaseUrl();

        // 根据启动模式建立连接
        switch (mode) {
            case LOCAL:
                // 本地模式：仅建立本地连接
                localConnection.connect(localUrl, token);
                LOG.info("本地模式：仅建立本地连接");
                break;

            case HOTSPOT:
                // 热点模式：仅建立热点连接（视为本地）
                final String hotspotUrl = ClientConfig.getInstance().getHotspotBaseUrl();
                if (hotspotUrl != null) {
                    localConnection.connect(hotspotUrl, token);
                    LOG.info("热点模式：建立热点连接");
                } else {
                    localConnection.connect(localUrl, token);
                    LOG.warn("热点地址未配置，回退到本地连接");
                }
                break;

            case CLOUD:
                // 云端模式：建立本地和云端双连接
                final String cloudUrl = ClientConfig.getInstance().getCloudBaseUrl();
                if (cloudUrl != null) {
                    // 本地连接用于AI隐私数据
                    localConnection.connect(localUrl, token);
                    // 云端连接用于真人聊天
                    cloudConnection.connect(cloudUrl, token);
                    LOG.info("云端模式：建立本地和云端双连接");
                } else {
                    localConnection.connect(localUrl, token);
                    LOG.warn("云端地址未配置，仅建立本地连接");
                }
                break;

            default:
                localConnection.connect(localUrl, token);
                break;
        }
    }

    /**
     * 发送消息（根据消息类型自动选择连接）
     *
     * @param type 消息类型
     * @param data 消息数据
     */
    public void send(final String type, final Map<String, Object> data) {
        final boolean useLocal = shouldUseLocalConnection(type);
        if (useLocal) {
            sendLocal(type, data);
        } else {
            sendCloud(type, data);
        }
    }

    /**
     * 发送消息到本地连接
     *
     * @param type 消息类型
     * @param data 消息数据
     */
    public void sendLocal(final String type, final Map<String, Object> data) {
        if (!localConnection.isConnected()) {
            LOG.warn("本地 WebSocket 未连接，消息发送失败: type={}", type);
            return;
        }
        localConnection.send(type, data);
    }

    /**
     * 发送消息到云端连接
     *
     * @param type 消息类型
     * @param data 消息数据
     */
    public void sendCloud(final String type, final Map<String, Object> data) {
        if (!cloudConnection.isConnected()) {
            LOG.warn("云端 WebSocket 未连接，尝试使用本地连接: type={}", type);
            if (localConnection.isConnected()) {
                localConnection.send(type, data);
            } else {
                LOG.error("所有 WebSocket 未连接，消息发送失败: type={}", type);
            }
            return;
        }
        cloudConnection.send(type, data);
    }

    /**
     * 判断消息是否应该使用本地连接
     *
     * @param messageType 消息类型
     * @return 是否使用本地连接
     */
    private boolean shouldUseLocalConnection(final String messageType) {
        // AI相关消息和向量检索消息使用本地连接
        if (messageType.startsWith("AI_") || messageType.startsWith("VECTOR_")) {
            return true;
        }
        return false;
    }

    /**
     * 关闭所有连接
     */
    public void close() {
        localConnection.close();
        cloudConnection.close();
        LOG.info("所有 WebSocket 连接已关闭");
    }

    /**
     * 是否已连接（任意一个连接可用即返回true）
     *
     * @return 连接状态
     */
    public boolean isConnected() {
        return localConnection.isConnected() || cloudConnection.isConnected();
    }

    /**
     * 本地连接是否可用
     *
     * @return 本地连接状态
     */
    public boolean isLocalConnected() {
        return localConnection.isConnected();
    }

    /**
     * 云端连接是否可用
     *
     * @return 云端连接状态
     */
    public boolean isCloudConnected() {
        return cloudConnection.isConnected();
    }

    /**
     * 获取本地连接重连次数
     *
     * @return 本地重连次数
     */
    public int getLocalReconnectAttempts() {
        return localConnection.getReconnectAttempts();
    }

    /**
     * 获取云端连接重连次数
     *
     * @return 云端重连次数
     */
    public int getCloudReconnectAttempts() {
        return cloudConnection.getReconnectAttempts();
    }
}