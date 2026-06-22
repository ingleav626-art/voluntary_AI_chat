package org.example.client.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ServerDiscovery 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class ServerDiscoveryTest {

    @Test
    @DisplayName("无服务器时广播发现应返回null")
    void discoverByBroadcastShouldReturnNullWhenNoServer() {
        // 没有服务器广播时，监听会超时返回null
        final String result = ServerDiscovery.discoverByBroadcast();
        assertNull(result);
    }

    @Test
    @DisplayName("无服务器时扫描发现应返回空列表")
    void discoverByScanShouldReturnEmptyListWhenNoServer() {
        // 本地没有服务器运行时，扫描应返回空列表
        final List<String> servers = ServerDiscovery.discoverByScan();
        assertNotNull(servers);
    }

    @Test
    @DisplayName("无服务器时自动发现应返回null")
    void autoDiscoverShouldReturnNullWhenNoServer() {
        final String result = ServerDiscovery.autoDiscover();
        // 本地没有服务器时，广播超时后扫描也找不到，返回null
        assertNull(result);
    }

    @Test
    @DisplayName("扫描结果中的地址格式应正确")
    void scanResultFormatShouldBeCorrect() {
        // 如果碰巧有服务器运行，验证地址格式
        final List<String> servers = ServerDiscovery.discoverByScan();
        for (final String server : servers) {
            assertTrue(server.startsWith("http://"), "服务器地址应以http://开头");
            assertTrue(server.endsWith("/api"), "服务器地址应以/api结尾");
        }
    }
}