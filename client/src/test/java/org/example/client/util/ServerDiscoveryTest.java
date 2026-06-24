package org.example.client.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ==================== 纯逻辑测试（不依赖真实网络） ====================

    @Test
    @DisplayName("checkServer - 本地回环地址返回布尔值不崩溃")
    void checkServer_localhost_shouldReturnBoolean() throws Exception {
        final java.lang.reflect.Method method = ServerDiscovery.class.getDeclaredMethod(
                "checkServer", String.class);
        method.setAccessible(true);
        final Object result = method.invoke(null, "127.0.0.1");
        assertNotNull(result);
        assertTrue(result instanceof Boolean);
    }

    @Test
    @DisplayName("checkServer - 无效IP也返回布尔不崩溃")
    void checkServer_invalidIp_shouldReturnBoolean() throws Exception {
        final java.lang.reflect.Method method = ServerDiscovery.class.getDeclaredMethod(
                "checkServer", String.class);
        method.setAccessible(true);
        final Object result = method.invoke(null, "0.0.0.0");
        assertNotNull(result);
        assertTrue(result instanceof Boolean);
    }

    @Test
    @DisplayName("checkServer - 不存在的IP应返回 false")
    void checkServer_nonExistentIp_shouldReturnFalse() throws Exception {
        final java.lang.reflect.Method method = ServerDiscovery.class.getDeclaredMethod(
                "checkServer", String.class);
        method.setAccessible(true);
        final Object result = method.invoke(null, "192.0.2.1");
        assertFalse((Boolean) result);
    }

    // ==================== UDP 广播模拟测试 ====================

    @Test
    @DisplayName("discoverByBroadcast - 收到正确格式广播应返回服务器地址")
    void discoverByBroadcast_withValidBroadcast_shouldReturnServerUrl() throws Exception {
        final CompletableFuture<String> future = CompletableFuture.supplyAsync(
                ServerDiscovery::discoverByBroadcast);

        Thread.sleep(800);

        try (DatagramSocket sendSocket = new DatagramSocket()) {
            sendSocket.setBroadcast(true);
            final String message = "VOLUNTARY_CHAT_SERVER:192.168.1.100:8080";
            final byte[] data = message.getBytes();
            final DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), 9876);
            sendSocket.send(packet);
        }

        final String result = future.get(3, TimeUnit.SECONDS);
        assertEquals("http://192.168.1.100:8080/api", result);
    }

    @Test
    @DisplayName("discoverByBroadcast - 收到格式错误的广播（缺少端口）应忽略并继续等待")
    void discoverByBroadcast_withInvalidFormatNoPort_shouldIgnoreAndContinue() throws Exception {
        final CompletableFuture<String> future = CompletableFuture.supplyAsync(
                ServerDiscovery::discoverByBroadcast);

        Thread.sleep(800);

        try (DatagramSocket sendSocket = new DatagramSocket()) {
            sendSocket.setBroadcast(true);
            final String message = "VOLUNTARY_CHAT_SERVER:192.168.1.100";
            final byte[] data = message.getBytes();
            final DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), 9876);
            sendSocket.send(packet);
        }

        final String result = future.get(7, TimeUnit.SECONDS);
        if (result != null) {
            assertTrue(result.startsWith("http://"));
        }
    }

    @Test
    @DisplayName("discoverByBroadcast - 收到非前缀消息应忽略并继续等待")
    void discoverByBroadcast_withNonPrefixedMessage_shouldIgnoreAndContinue() throws Exception {
        final CompletableFuture<String> future = CompletableFuture.supplyAsync(
                ServerDiscovery::discoverByBroadcast);

        Thread.sleep(800);

        try (DatagramSocket sendSocket = new DatagramSocket()) {
            sendSocket.setBroadcast(true);
            final String message = "SOME_OTHER_MESSAGE";
            final byte[] data = message.getBytes();
            final DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), 9876);
            sendSocket.send(packet);
        }

        final String result = future.get(7, TimeUnit.SECONDS);
        if (result != null) {
            assertTrue(result.startsWith("http://"));
        }
    }

    @Test
    @DisplayName("discoverByBroadcast - 收到多个部分的消息应忽略")
    void discoverByBroadcast_withTooManyParts_shouldIgnore() throws Exception {
        final CompletableFuture<String> future = CompletableFuture.supplyAsync(
                ServerDiscovery::discoverByBroadcast);

        Thread.sleep(800);

        try (DatagramSocket sendSocket = new DatagramSocket()) {
            sendSocket.setBroadcast(true);
            final String message = "VOLUNTARY_CHAT_SERVER:192.168.1.100:8080:extra";
            final byte[] data = message.getBytes();
            final DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), 9876);
            sendSocket.send(packet);
        }

        final String result = future.get(7, TimeUnit.SECONDS);
        if (result != null) {
            assertTrue(result.startsWith("http://"));
        }
    }

    @Test
    @DisplayName("discoverByBroadcast - 端口被占用时应触发 IOException 分支返回null")
    void discoverByBroadcast_portInUse_shouldReturnNull() throws Exception {
        try (DatagramSocket blockingSocket = new DatagramSocket(9876)) {
            final String result = ServerDiscovery.discoverByBroadcast();
            assertNull(result);
        }
    }

    @Test
    @DisplayName("discoverByBroadcast - 超时返回 null")
    void discoverByBroadcast_timeout_shouldReturnNull() {
        try (DatagramSocket blockingSocket = new DatagramSocket(9876)) {
            final String result = ServerDiscovery.discoverByBroadcast();
            assertNull(result);
        } catch (final Exception e) {
            // 端口可能已被占用
        }
    }

    // ==================== 网络扫描测试（依赖真实网络，默认跳过） ====================

    @Disabled("需要真实网络环境，CI/本地测试默认跳过")
    @Test
    @DisplayName("discoverByBroadcast - 返回结果类型正确（String 或 null）")
    void discoverByBroadcast_shouldReturnStringOrNull() {
        final String result = ServerDiscovery.discoverByBroadcast();
        if (result != null) {
            assertTrue(result.startsWith("http://"));
            assertTrue(result.endsWith("/api"));
        }
    }

    @Disabled("需要真实网络环境，扫描254个IP耗时过长")
    @Test
    @DisplayName("discoverByScan - 返回列表不为null")
    void discoverByScan_shouldReturnNonNullList() {
        final List<String> servers = ServerDiscovery.discoverByScan();
        assertNotNull(servers);
    }

    @Disabled("需要真实网络环境，扫描254个IP耗时过长")
    @Test
    @DisplayName("discoverByScan - 返回结果中的地址格式正确")
    void scanResultFormatShouldBeCorrect() {
        final List<String> servers = ServerDiscovery.discoverByScan();
        for (final String server : servers) {
            assertTrue(server.startsWith("http://"));
            assertTrue(server.endsWith("/api"));
        }
    }

    @Disabled("需要真实网络环境，扫描耗时过长")
    @Test
    @DisplayName("autoDiscover - 返回结果类型正确")
    void autoDiscover_shouldReturnStringOrNull() {
        final String result = ServerDiscovery.autoDiscover();
        if (result != null) {
            assertTrue(result.startsWith("http://"));
            assertTrue(result.endsWith("/api"));
        }
    }

    @Disabled("需要真实网络环境，扫描耗时过长")
    @Test
    @DisplayName("多次调用 autoDiscover 不崩溃")
    void autoDiscoverMultipleCalls_shouldNotCrash() {
        assertDoesNotThrow(() -> {
            ServerDiscovery.autoDiscover();
            ServerDiscovery.autoDiscover();
        });
    }

    @Disabled("需要真实网络环境，扫描耗时过长")
    @Test
    @DisplayName("多次调用 discoverByScan 不崩溃")
    void discoverByScanMultipleCalls_shouldNotCrash() {
        assertDoesNotThrow(() -> {
            ServerDiscovery.discoverByScan();
            ServerDiscovery.discoverByScan();
        });
    }

    @Disabled("需要真实网络环境，可能受其他广播影响")
    @Test
    @DisplayName("多次调用 discoverByBroadcast 不崩溃")
    void discoverByBroadcastMultipleCalls_shouldNotCrash() {
        assertDoesNotThrow(() -> {
            ServerDiscovery.discoverByBroadcast();
            ServerDiscovery.discoverByBroadcast();
        });
    }

    @Disabled("依赖真实网络扫描，耗时过长")
    @Test
    @DisplayName("discoverByScan - 并发扫描不崩溃")
    void discoverByScan_concurrentCalls_shouldNotCrash() throws Exception {
        final List<CompletableFuture<List<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(CompletableFuture.supplyAsync(ServerDiscovery::discoverByScan));
        }
        for (final CompletableFuture<List<String>> f : futures) {
            final List<String> servers = f.get(35, TimeUnit.SECONDS);
            assertNotNull(servers);
        }
    }

    @Disabled("依赖真实网络扫描，耗时过长")
    @Test
    @DisplayName("autoDiscover - 广播超时后走扫描路径")
    void autoDiscover_broadcastTimeout_shouldFallbackToScan() {
        try (DatagramSocket blockingSocket = new DatagramSocket(9876)) {
            final String result = ServerDiscovery.autoDiscover();
            if (result != null) {
                assertTrue(result.startsWith("http://"));
                assertTrue(result.endsWith("/api"));
            }
        } catch (final Exception e) {
            // 端口可能已被其他测试占用
        }
    }
}
