package org.example.client.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.example.client.util.ServerDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务器连接管理器
 *
 * <p>
 * 管理三种服务器模式的连接和切换：
 * <ul>
 * <li>本地模式：内嵌后端，H2数据库</li>
 * <li>热点模式：局域网测试服务器</li>
 * <li>云端模式：公网服务器</li>
 * </ul>
 * </p>
 *
 * <p>
 * 启动策略：
 * <ol>
 * <li>检查环境变量 SERVER_MODE，确定启动模式</li>
 * <li>同时检查云端服务器连接状态（异步）</li>
 * <li>默认使用本地模式，云端可用时覆盖</li>
 * <li>隐私模式开启时强制使用本地模式</li>
 * </ol>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class ServerConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(ServerConnectionManager.class);

    /** 环境变量：启动模式 */
    private static final String ENV_SERVER_MODE = "SERVER_MODE";

    /** 环境变量：云端服务器地址 */
    private static final String ENV_CLOUD_SERVER_URL = "CLOUD_SERVER_URL";

    /** 环境变量：热点服务器地址 */
    private static final String ENV_HOTSPOT_SERVER_URL = "HOTSPOT_SERVER_URL";

    /** 环境变量：隐私模式 */
    private static final String ENV_PRIVACY_MODE = "PRIVACY_MODE";

    /** 连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    /** 读取超时时间（毫秒） */
    private static final int READ_TIMEOUT_MS = 3000;

    /** 异步检查线程池 */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 当前启动模式 */
    private ServerMode currentMode;

    /** 云端服务器地址 */
    private String cloudServerUrl;

    /** 热点服务器地址 */
    private String hotspotServerUrl;

    /** 隐私模式开关 */
    private boolean privacyModeEnabled;

    /** 云端服务器连接状态 */
    private boolean cloudServerAvailable;

    /** 本地服务器连接状态 */
    private boolean localServerAvailable;

    /** 单例实例 */
    private static ServerConnectionManager instance;

    private ServerConnectionManager() {
        // 从环境变量读取配置
        this.currentMode = ServerMode.fromCode(System.getenv(ENV_SERVER_MODE));
        this.cloudServerUrl = System.getenv(ENV_CLOUD_SERVER_URL);
        this.hotspotServerUrl = System.getenv(ENV_HOTSPOT_SERVER_URL);
        this.privacyModeEnabled = "true".equalsIgnoreCase(System.getenv(ENV_PRIVACY_MODE));

        LOG.info("服务器连接管理器初始化: mode={}, privacy={}, cloud={}, hotspot={}",
                currentMode.getDescription(), privacyModeEnabled, cloudServerUrl, hotspotServerUrl);
    }

    /**
     * 获取单例实例
     */
    public static synchronized ServerConnectionManager getInstance() {
        if (instance == null) {
            instance = new ServerConnectionManager();
        }
        return instance;
    }

    /**
     * 异步检查服务器连接状态
     *
     * <p>
     * 同时检查本地和云端服务器，云端可用时覆盖本地连接。
     * </p>
     *
     * @return CompletableFuture，完成后返回当前可用的服务器地址
     */
    public CompletableFuture<String> checkServerAvailabilityAsync() {
        LOG.info("开始异步检查服务器连接状态...");

        // 同时检查本地和云端服务器
        final CompletableFuture<Boolean> localCheck = checkServerAsync(ServerMode.LOCAL.getDefaultBaseUrl());
        final CompletableFuture<Boolean> cloudCheck = cloudServerUrl != null
                ? checkServerAsync(cloudServerUrl)
                : CompletableFuture.completedFuture(false);

        return CompletableFuture.allOf(localCheck, cloudCheck)
                .thenApply(v -> {
                    localServerAvailable = localCheck.join();
                    cloudServerAvailable = cloudCheck.join();

                    LOG.info("服务器检查完成: local={}, cloud={}", localServerAvailable, cloudServerAvailable);

                    // 根据隐私模式和服务器状态决定最终连接
                    return determineFinalConnection();
                });
    }

    /**
     * 异步检查单个服务器连接状态
     *
     * @param serverUrl 服务器地址
     * @return CompletableFuture，返回连接状态
     */
    private CompletableFuture<Boolean> checkServerAsync(final String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                final String healthUrl = serverUrl.replace("/api", "") + "/api/local/health";
                final URL url = new URL(healthUrl);
                final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");

                final int responseCode = conn.getResponseCode();
                conn.disconnect();

                final boolean available = responseCode == HttpURLConnection.HTTP_OK;
                LOG.debug("服务器检查: {} -> {}", serverUrl, available);
                return available;
            } catch (final IOException e) {
                LOG.debug("服务器检查失败: {} -> {}", serverUrl, e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * 根据隐私模式和服务器状态决定最终连接
     *
     * @return 最终可用的服务器地址
     */
    private String determineFinalConnection() {
        // 隐私模式开启时强制使用本地模式
        if (privacyModeEnabled) {
            LOG.info("隐私模式已开启，强制使用本地模式");
            currentMode = ServerMode.LOCAL;
            return ServerMode.LOCAL.getDefaultBaseUrl();
        }

        // 根据启动模式决定连接
        switch (currentMode) {
            case HOTSPOT:
                // 热点模式：连接局域网测试服务器
                if (hotspotServerUrl != null && checkServerSync(hotspotServerUrl)) {
                    LOG.info("热点模式：连接指定热点服务器 {}", hotspotServerUrl);
                    return hotspotServerUrl;
                } else {
                    // 没指定或不可用：自动发现局域网服务器（先监听UDP广播，再扫描）
                    LOG.info("热点模式：未指定热点服务器或不可用，开始自动发现...");
                    final String discoveredUrl = ServerDiscovery.autoDiscover();
                    if (discoveredUrl != null) {
                        LOG.info("热点模式：自动发现服务器 {}", discoveredUrl);
                        return discoveredUrl;
                    }
                    LOG.warn("热点模式：未发现任何服务器，回退到本地模式");
                    currentMode = ServerMode.LOCAL;
                    return ServerMode.LOCAL.getDefaultBaseUrl();
                }

            case CLOUD:
                // 云端模式：连接公网服务器
                if (cloudServerUrl != null && cloudServerAvailable) {
                    LOG.info("云端模式：连接云端服务器 {}", cloudServerUrl);
                    return cloudServerUrl;
                } else {
                    LOG.warn("云端服务器不可用，回退到本地模式");
                    currentMode = ServerMode.LOCAL;
                    return ServerMode.LOCAL.getDefaultBaseUrl();
                }

            case LOCAL:
                // 本地模式：默认使用本地，根据异步检查目标决定覆盖策略
                final String checkTarget = System.getProperty("app.async-check-target", "hotspot");
                if ("hotspot".equals(checkTarget)) {
                    // 测试包：检查热点服务器，热点可用时覆盖本地
                    final String hotspot = discoverHotspotServer();
                    if (hotspot != null) {
                        LOG.info("测试包模式：发现热点服务器 {}", hotspot);
                        return hotspot;
                    }
                    LOG.info("测试包模式：未发现热点服务器，使用本地服务器");
                    return ServerMode.LOCAL.getDefaultBaseUrl();
                } else {
                    // 客户包/默认：云端可用时覆盖本地
                    if (cloudServerAvailable && !privacyModeEnabled) {
                        LOG.info("客户包模式：云端服务器可用，覆盖本地连接");
                        return cloudServerUrl;
                    } else if (localServerAvailable) {
                        LOG.info("客户包模式：仅使用本地服务器");
                        return ServerMode.LOCAL.getDefaultBaseUrl();
                    } else {
                        LOG.warn("本地服务器不可用，尝试云端服务器");
                        if (cloudServerAvailable) {
                            return cloudServerUrl;
                        } else {
                            LOG.error("所有服务器不可用，使用本地默认地址");
                            return ServerMode.LOCAL.getDefaultBaseUrl();
                        }
                    }
                }

            default:
                return ServerMode.LOCAL.getDefaultBaseUrl();
        }
    }

    /**
     * 同步检查服务器连接状态
     *
     * @param serverUrl 服务器地址
     * @return 连接状态
     */
    private boolean checkServerSync(final String serverUrl) {
        try {
            final CompletableFuture<Boolean> check = checkServerAsync(serverUrl);
            return check.get();
        } catch (final Exception e) {
            LOG.error("同步检查服务器失败: {}", serverUrl, e);
            return false;
        }
    }

    /**
     * 判断群聊是否需要云端连接
     *
     * <p>
     * 群聊包含AI时需要云端连接（真人消息转发），
     * 纯AI聊天（不含群主）可以使用本地模式。
     * </p>
     *
     * @param hasAi           群聊是否包含AI
     * @param hasHumanMembers 群聊是否包含真人成员（除群主外）
     * @return 是否需要云端连接
     */
    public boolean requiresCloudConnection(final boolean hasAi, final boolean hasHumanMembers) {
        // 隐私模式开启时强制本地
        if (privacyModeEnabled) {
            LOG.info("隐私模式开启，群聊强制使用本地模式");
            return false;
        }

        // 群聊包含真人成员（除群主外）时需要云端
        if (hasHumanMembers) {
            LOG.info("群聊包含真人成员，需要云端连接");
            return true;
        }

        // 纯AI聊天（不含群主）可以使用本地
        if (hasAi && !hasHumanMembers) {
            LOG.info("纯AI群聊（不含真人成员），可以使用本地模式");
            return false;
        }

        // 默认需要云端
        return true;
    }

    /**
     * 切换隐私模式
     *
     * @param enabled 是否开启隐私模式
     */
    public void setPrivacyMode(final boolean enabled) {
        this.privacyModeEnabled = enabled;
        LOG.info("隐私模式切换: {}", enabled ? "开启" : "关闭");

        if (enabled) {
            // 强制切换到本地模式
            currentMode = ServerMode.LOCAL;
            LOG.info("隐私模式开启，强制使用本地服务器");
        } else {
            // 重新检查服务器状态
            checkServerAvailabilityAsync();
        }
    }

    /**
     * 获取当前启动模式
     */
    public ServerMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 获取隐私模式状态
     */
    public boolean isPrivacyModeEnabled() {
        return privacyModeEnabled;
    }

    /**
     * 获取云端服务器连接状态
     */
    public boolean isCloudServerAvailable() {
        return cloudServerAvailable;
    }

    /**
     * 获取本地服务器连接状态
     */
    public boolean isLocalServerAvailable() {
        return localServerAvailable;
    }

    /**
     * 设置热点服务器地址（用于 Launcher 在 HOTSPOT 模式下发现后设置）
     *
     * @param url 热点服务器地址
     */
    public void setHotspotServerUrl(final String url) {
        this.hotspotServerUrl = url;
        LOG.info("热点服务器地址已设置: {}", url);
    }

    /**
     * 发现热点服务器
     *
     * <p>
     * 先尝试连接环境变量指定的热点服务器，如果未指定或不可用，则自动发现局域网服务器。
     * </p>
     *
     * @return 热点服务器地址，未发现时返回 null
     */
    private String discoverHotspotServer() {
        // 如果环境变量指定了热点服务器，优先使用
        if (hotspotServerUrl != null && checkServerSync(hotspotServerUrl)) {
            LOG.info("使用指定的热点服务器 {}", hotspotServerUrl);
            return hotspotServerUrl;
        }

        // 未指定或不可用时自动发现
        LOG.info("开始自动发现热点服务器...");
        final String discoveredUrl = ServerDiscovery.autoDiscover();
        if (discoveredUrl != null) {
            LOG.info("自动发现热点服务器 {}", discoveredUrl);
            return discoveredUrl;
        }

        LOG.warn("未发现任何热点服务器");
        return null;
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executor.shutdown();
        LOG.info("服务器连接管理器已关闭");
    }
}