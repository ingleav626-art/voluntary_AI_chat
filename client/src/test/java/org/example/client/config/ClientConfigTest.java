package org.example.client.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClientConfig测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class ClientConfigTest {

    private ClientConfig clientConfig;

    @BeforeEach
    void setUp() {
        clientConfig = ClientConfig.getInstance();
    }

    @Test
    void testGetInstance() {
        final ClientConfig instance1 = ClientConfig.getInstance();
        final ClientConfig instance2 = ClientConfig.getInstance();
        assertEquals(instance1, instance2); // 单例模式
    }

    @Test
    void testSetAndGetBaseUrl() {
        final String testUrl = "http://test-server:8080/api";
        clientConfig.setBaseUrl(testUrl);
        assertEquals(testUrl, clientConfig.getBaseUrl());
    }

    @Test
    void testSetAndGetLocalBaseUrl() {
        final String testUrl = "http://localhost:9090/api";
        clientConfig.setLocalBaseUrl(testUrl);
        assertEquals(testUrl, clientConfig.getLocalBaseUrl());
    }

    @Test
    void testSetAndGetCloudBaseUrl() {
        final String testUrl = "https://cloud-server.com/api";
        clientConfig.setCloudBaseUrl(testUrl);
        assertEquals(testUrl, clientConfig.getCloudBaseUrl());
    }

    @Test
    void testSetAndGetHotspotBaseUrl() {
        final String testUrl = "http://192.168.1.100:8080/api";
        clientConfig.setHotspotBaseUrl(testUrl);
        assertEquals(testUrl, clientConfig.getHotspotBaseUrl());
    }

    @Test
    void testGetBaseUrlByMode_Local() {
        final String localUrl = "http://localhost:8080/api";
        clientConfig.setLocalBaseUrl(localUrl);
        assertEquals(localUrl, clientConfig.getBaseUrlByMode(ServerMode.LOCAL));
    }

    @Test
    void testGetBaseUrlByMode_Hotspot() {
        final String hotspotUrl = "http://192.168.1.100:8080/api";
        clientConfig.setHotspotBaseUrl(hotspotUrl);
        assertEquals(hotspotUrl, clientConfig.getBaseUrlByMode(ServerMode.HOTSPOT));
    }

    @Test
    void testGetBaseUrlByMode_Hotspot_Null() {
        // 如果热点地址未配置，返回本地地址
        clientConfig.setHotspotBaseUrl(null);
        final String localUrl = "http://localhost:8080/api";
        clientConfig.setLocalBaseUrl(localUrl);
        assertEquals(localUrl, clientConfig.getBaseUrlByMode(ServerMode.HOTSPOT));
    }

    @Test
    void testGetBaseUrlByMode_Cloud() {
        final String cloudUrl = "https://cloud-server.com/api";
        clientConfig.setCloudBaseUrl(cloudUrl);
        assertEquals(cloudUrl, clientConfig.getBaseUrlByMode(ServerMode.CLOUD));
    }

    @Test
    void testGetBaseUrlByMode_Cloud_Null() {
        // 如果云端地址未配置，返回本地地址
        clientConfig.setCloudBaseUrl(null);
        final String localUrl = "http://localhost:8080/api";
        clientConfig.setLocalBaseUrl(localUrl);
        assertEquals(localUrl, clientConfig.getBaseUrlByMode(ServerMode.CLOUD));
    }

    @Test
    void testGetConnectTimeout() {
        // 默认超时时间
        assertTrue(clientConfig.getConnectTimeout() > 0);
    }

    @Test
    void testGetReadTimeout() {
        // 默认超时时间
        assertTrue(clientConfig.getReadTimeout() > 0);
    }
}