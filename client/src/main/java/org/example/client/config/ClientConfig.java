package org.example.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端配置管理（单例，支持双服务器配置）
 *
 * <p>
 * 支持三种启动模式的服务器配置：
 * <ul>
 * <li>LOCAL - 本地服务器（内嵌后端，H2数据库）</li>
 * <li>HOTSPOT - 热点服务器（局域网测试）</li>
 * <li>CLOUD - 云端服务器（公网服务器）</li>
 * </ul>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConfig.class);

    private static final ClientConfig INSTANCE = new ClientConfig();

    private static final String DEFAULT_CONFIG_FILE = "application-client.properties";

    private static final String DEFAULT_LOCAL_BASE_URL = "http://localhost:8080/api";

    private static final int DEFAULT_CONNECT_TIMEOUT = 10;

    private static final int DEFAULT_READ_TIMEOUT = 30;

    /** 配置文件名称 */
    private String configFile = DEFAULT_CONFIG_FILE;

    /** 本地服务器 Base URL（内嵌后端） */
    private String localBaseUrl = DEFAULT_LOCAL_BASE_URL;

    /** 云端服务器 Base URL（公网服务器） */
    private String cloudBaseUrl;

    /** 热点服务器 Base URL（局域网测试） */
    private String hotspotBaseUrl;

    /** 当前使用的服务器 Base URL（根据启动模式动态决定） */
    private String currentBaseUrl = DEFAULT_LOCAL_BASE_URL;

    /** 连接超时（秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;

    private ClientConfig() {
        // 单例模式，禁止外部实例化
    }

    public static ClientConfig getInstance() {
        return INSTANCE;
    }

    /**
     * 设置配置文件名称
     *
     * @param configFile 配置文件名称（如 application-hotspot.properties）
     */
    public void setConfigFile(final String configFile) {
        this.configFile = configFile;
    }

    /**
     * 设置当前服务器 Base URL
     *
     * @param baseUrl 服务器 Base URL
     */
    public void setBaseUrl(final String baseUrl) {
        this.currentBaseUrl = baseUrl;
        LOG.info("当前服务器地址设置为: {}", baseUrl);
    }

    /**
     * 设置本地服务器 Base URL
     *
     * @param baseUrl 本地服务器 Base URL
     */
    public void setLocalBaseUrl(final String baseUrl) {
        this.localBaseUrl = baseUrl;
        LOG.debug("本地服务器地址设置为: {}", baseUrl);
    }

    /**
     * 设置云端服务器 Base URL
     *
     * @param baseUrl 云端服务器 Base URL
     */
    public void setCloudBaseUrl(final String baseUrl) {
        this.cloudBaseUrl = baseUrl;
        LOG.debug("云端服务器地址设置为: {}", baseUrl);
    }

    /**
     * 设置热点服务器 Base URL
     *
     * @param baseUrl 热点服务器 Base URL
     */
    public void setHotspotBaseUrl(final String baseUrl) {
        this.hotspotBaseUrl = baseUrl;
        LOG.debug("热点服务器地址设置为: {}", baseUrl);
    }

    public void load() {
        try (final InputStream is = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (is == null) {
                LOG.warn("配置文件 {} 不存在，使用默认配置", configFile);
                return;
            }

            final Properties props = new Properties();
            props.load(is);

            // 读取本地服务器地址
            localBaseUrl = props.getProperty("client.local-base-url", DEFAULT_LOCAL_BASE_URL);

            // 读取云端服务器地址
            cloudBaseUrl = props.getProperty("client.cloud-base-url");

            // 读取热点服务器地址
            hotspotBaseUrl = props.getProperty("client.hotspot-base-url");

            // 读取当前服务器地址（如果配置文件中指定）
            currentBaseUrl = props.getProperty("client.base-url", localBaseUrl);

            // 读取超时配置
            connectTimeout = Integer.parseInt(props.getProperty("client.connect-timeout",
                    String.valueOf(DEFAULT_CONNECT_TIMEOUT)));
            readTimeout = Integer.parseInt(props.getProperty("client.read-timeout",
                    String.valueOf(DEFAULT_READ_TIMEOUT)));

            LOG.debug("配置加载完成: local={}, cloud={}, hotspot={}, current={}, connectTimeout={}, readTimeout={}",
                    localBaseUrl, cloudBaseUrl, hotspotBaseUrl, currentBaseUrl, connectTimeout, readTimeout);
        } catch (final IOException e) {
            LOG.error("加载配置文件失败", e);
        } catch (final NumberFormatException e) {
            LOG.error("配置参数格式错误", e);
        }
    }

    /**
     * 获取当前使用的服务器 Base URL
     *
     * @return 当前服务器 Base URL
     */
    public String getBaseUrl() {
        return currentBaseUrl;
    }

    /**
     * 获取本地服务器 Base URL
     *
     * @return 本地服务器 Base URL
     */
    public String getLocalBaseUrl() {
        return localBaseUrl;
    }

    /**
     * 获取云端服务器 Base URL
     *
     * @return 云端服务器 Base URL，未配置时返回null
     */
    public String getCloudBaseUrl() {
        return cloudBaseUrl;
    }

    /**
     * 获取热点服务器 Base URL
     *
     * @return 热点服务器 Base URL，未配置时返回null
     */
    public String getHotspotBaseUrl() {
        return hotspotBaseUrl;
    }

    /**
     * 根据启动模式获取对应的服务器 Base URL
     *
     * @param mode 启动模式
     * @return 对应的服务器 Base URL
     */
    public String getBaseUrlByMode(final ServerMode mode) {
        switch (mode) {
            case LOCAL:
                return localBaseUrl;
            case HOTSPOT:
                return hotspotBaseUrl != null ? hotspotBaseUrl : localBaseUrl;
            case CLOUD:
                return cloudBaseUrl != null ? cloudBaseUrl : localBaseUrl;
            default:
                return localBaseUrl;
        }
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }
}
