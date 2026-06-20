package org.example.client.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClientConfig 单元测试
 */
@DisplayName("ClientConfig 测试")
class ClientConfigTest {

    @Test
    @DisplayName("获取单例实例")
    void testGetInstance() {
        final ClientConfig instance1 = ClientConfig.getInstance();
        final ClientConfig instance2 = ClientConfig.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("默认配置值")
    void testDefaultValues() {
        final ClientConfig config = ClientConfig.getInstance();

        assertEquals("http://localhost:8080/api", config.getBaseUrl());
        assertEquals(10, config.getConnectTimeout());
        assertEquals(30, config.getReadTimeout());
    }

    @Test
    @DisplayName("加载配置文件（不存在时使用默认值）")
    void testLoadNonExistentConfig() {
        final ClientConfig config = ClientConfig.getInstance();
        config.load();

        // 配置文件不存在，应使用默认值
        assertNotNull(config.getBaseUrl());
        assertTrue(config.getConnectTimeout() > 0);
        assertTrue(config.getReadTimeout() > 0);
    }

    @Test
    @DisplayName("获取 Base URL")
    void testGetBaseUrl() {
        final ClientConfig config = ClientConfig.getInstance();
        final String baseUrl = config.getBaseUrl();

        assertNotNull(baseUrl);
        assertTrue(baseUrl.startsWith("http"));
    }

    @Test
    @DisplayName("获取连接超时")
    void testGetConnectTimeout() {
        final ClientConfig config = ClientConfig.getInstance();
        final int timeout = config.getConnectTimeout();

        assertTrue(timeout > 0);
        assertTrue(timeout <= 60);
    }

    @Test
    @DisplayName("获取读取超时")
    void testGetReadTimeout() {
        final ClientConfig config = ClientConfig.getInstance();
        final int timeout = config.getReadTimeout();

        assertTrue(timeout > 0);
        assertTrue(timeout <= 120);
    }
}