package org.example.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端配置管理（单例）
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConfig.class);

    private static final ClientConfig INSTANCE = new ClientConfig();

    private static final String CONFIG_FILE = "application-client.yml";

    private static final String DEFAULT_BASE_URL = "http://localhost:8080/api";

    private static final int DEFAULT_CONNECT_TIMEOUT = 10;

    private static final int DEFAULT_READ_TIMEOUT = 30;

    /** 服务端 Base URL */
    private String baseUrl = DEFAULT_BASE_URL;

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

    public void load() {
        try (final InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                LOG.warn("配置文件 {} 不存在，使用默认配置", CONFIG_FILE);
                return;
            }

            final Properties props = new Properties();
            props.load(is);

            baseUrl = props.getProperty("client.base-url", DEFAULT_BASE_URL);
            connectTimeout = Integer.parseInt(props.getProperty("client.connect-timeout",
                    String.valueOf(DEFAULT_CONNECT_TIMEOUT)));
            readTimeout = Integer.parseInt(props.getProperty("client.read-timeout",
                    String.valueOf(DEFAULT_READ_TIMEOUT)));

            LOG.debug("配置加载完成: baseUrl={}, connectTimeout={}, readTimeout={}",
                    baseUrl, connectTimeout, readTimeout);
        } catch (final IOException e) {
            LOG.error("加载配置文件失败", e);
        } catch (final NumberFormatException e) {
            LOG.error("配置参数格式错误", e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }
}

