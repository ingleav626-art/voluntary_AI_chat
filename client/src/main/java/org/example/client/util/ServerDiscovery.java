package org.example.client.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务器发现工具
 *
 * <p>
 * 通过UDP广播监听或主动扫描局域网发现服务器地址。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ServerDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(ServerDiscovery.class);

    /** UDP广播监听端口 */
    private static final int BROADCAST_PORT = 9876;

    /** 服务器HTTP端口 */
    private static final int SERVER_PORT = 8080;

    /** UDP监听超时（毫秒） */
    private static final int UDP_TIMEOUT_MS = 5000;

    /** TCP连接超时（毫秒） */
    private static final int TCP_TIMEOUT_MS = 500;

    /** 扫描并发线程数 */
    private static final int SCAN_THREADS = 20;

    /** 广播消息格式 */
    private static final String BROADCAST_MESSAGE_PREFIX = "VOLUNTARY_CHAT_SERVER:";

    /** 常见局域网IP段（只扫描一个IP段，减少扫描时间） */
    private static final String[] COMMON_IP_SEGMENTS = {
            "192.168.1." // 路由器常见（只扫描一个IP段）
    };

    private ServerDiscovery() {
        // 工具类，禁止实例化
    }

    /**
     * 监听UDP广播发现服务器
     *
     * @return 服务器地址（格式：http://IP:8080/api），如果未发现返回null
     */
    public static String discoverByBroadcast() {
        try (final DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(BROADCAST_PORT));
            socket.setSoTimeout(UDP_TIMEOUT_MS);

            LOG.info("开始监听服务器广播，端口: {}", BROADCAST_PORT);

            final byte[] buffer = new byte[256];
            final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            socket.receive(packet);

            final String message = new String(packet.getData(), 0, packet.getLength());
            LOG.debug("收到广播消息: {} from {}", message, packet.getAddress());

            if (message.startsWith(BROADCAST_MESSAGE_PREFIX)) {
                final String[] parts = message.substring(BROADCAST_MESSAGE_PREFIX.length()).split(":");
                if (parts.length == 2) {
                    final String ip = parts[0];
                    final int port = Integer.parseInt(parts[1]);
                    final String serverUrl = "http://" + ip + ":" + port + "/api";
                    LOG.info("发现服务器: {}", serverUrl);
                    return serverUrl;
                }
            }
        } catch (final SocketTimeoutException e) {
            LOG.info("UDP广播监听超时，未发现服务器");
        } catch (final IOException e) {
            LOG.error("监听UDP广播失败", e);
        }

        return null;
    }

    /**
     * 主动扫描局域网发现服务器
     *
     * @return 服务器地址列表
     */
    public static List<String> discoverByScan() {
        LOG.info("开始扫描局域网查找服务器");

        final List<String> servers = new ArrayList<>();
        final ExecutorService executor = Executors.newFixedThreadPool(SCAN_THREADS);

        try {
            final List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (final String segment : COMMON_IP_SEGMENTS) {
                for (int i = 1; i <= 254; i++) {
                    final String ip = segment + i;
                    futures.add(CompletableFuture.runAsync(() -> {
                        if (checkServer(ip)) {
                            servers.add("http://" + ip + ":" + SERVER_PORT + "/api");
                        }
                    }, executor));
                }
            }

            // 等待所有扫描完成，最多30秒
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);

        } catch (final Exception e) {
            LOG.error("扫描局域网失败", e);
        } finally {
            executor.shutdown();
        }

        LOG.info("扫描完成，发现 {} 个服务器", servers.size());
        return servers;
    }

    /**
     * 检查指定IP是否有服务器运行
     *
     * @param ip IP地址
     * @return 是否有服务器
     */
    private static boolean checkServer(final String ip) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, SERVER_PORT), TCP_TIMEOUT_MS);
            LOG.debug("发现服务器: {}", ip);
            return true;
        } catch (final IOException e) {
            // 连接失败，该IP没有服务器
            return false;
        }
    }

    /**
     * 自动发现服务器（先监听广播，超时后扫描）
     *
     * @return 服务器地址，如果未发现返回null
     */
    public static String autoDiscover() {
        // 先尝试监听广播
        final String broadcastServer = discoverByBroadcast();
        if (broadcastServer != null) {
            return broadcastServer;
        }

        // 广播超时，主动扫描
        final List<String> scannedServers = discoverByScan();
        if (!scannedServers.isEmpty()) {
            // 返回第一个发现的服务器
            return scannedServers.get(0);
        }

        LOG.warn("未发现任何服务器");
        return null;
    }
}