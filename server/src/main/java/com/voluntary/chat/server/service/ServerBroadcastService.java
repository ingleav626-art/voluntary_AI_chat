package com.voluntary.chat.server.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * 服务器广播服务
 *
 * <p>
 * 通过UDP广播向局域网宣告服务器地址，便于客户端自动发现。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Service
public class ServerBroadcastService {

    private static final Logger LOG = LoggerFactory.getLogger(ServerBroadcastService.class);

    /** UDP广播端口 */
    private static final int BROADCAST_PORT = 9876;

    /** 广播间隔（秒） */
    private static final long BROADCAST_INTERVAL_SECONDS = 5;

    /** 服务器HTTP端口 */
    private static final int SERVER_PORT = 8080;

    /** 广播消息格式 */
    private static final String BROADCAST_MESSAGE_FORMAT = "VOLUNTARY_CHAT_SERVER:%s:%d";

    /** UDP套接字 */
    private DatagramSocket socket;

    /** 定时任务执行器 */
    private ScheduledExecutorService scheduler;

    /** 是否正在广播 */
    private volatile boolean broadcasting;

    /**
     * 启动广播服务
     */
    public void startBroadcast() {
        if (broadcasting) {
            LOG.warn("广播服务已在运行");
            return;
        }

        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread t = new Thread(r, "server-broadcast");
                t.setDaemon(true);
                return t;
            });

            broadcasting = true;

            // 立即广播一次
            broadcast();

            // 定期广播
            scheduler.scheduleAtFixedRate(this::broadcast, BROADCAST_INTERVAL_SECONDS,
                    BROADCAST_INTERVAL_SECONDS, TimeUnit.SECONDS);

            LOG.info("服务器广播服务已启动，端口: {}", BROADCAST_PORT);
        } catch (final IOException e) {
            LOG.error("启动广播服务失败", e);
        }
    }

    /**
     * 停止广播服务
     */
    @PreDestroy
    public void stopBroadcast() {
        broadcasting = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (final InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        LOG.info("服务器广播服务已停止");
    }

    /**
     * 执行广播
     */
    private void broadcast() {
        try {
            // 获取本机所有网络接口的IP地址
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface networkInterface = interfaces.nextElement();

                // 跳过回环接口、未启用的接口、虚拟接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                // 过滤掉常见的虚拟网卡（WSL、VMware、VirtualBox等）
                final String interfaceName = networkInterface.getName().toLowerCase();
                if (interfaceName.contains("wsl") || interfaceName.contains("vmware")
                        || interfaceName.contains("virtualbox") || interfaceName.contains("docker")) {
                    LOG.debug("跳过虚拟网卡: {}", interfaceName);
                    continue;
                }

                final Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    final InetAddress address = addresses.nextElement();

                    // 只处理IPv4地址
                    if (address.isLoopbackAddress() || address.getHostAddress().contains(":")) {
                        continue;
                    }

                    final String ipAddress = address.getHostAddress();

                    // 过滤掉虚拟网络地址（VPN、Hyper-V等）
                    // 26.x.x.x - Radmin VPN
                    // 172.16-31.x.x - Hyper-V/Docker私有网络
                    if (isVirtualNetworkAddress(ipAddress)) {
                        LOG.debug("跳过虚拟网络地址: {}", ipAddress);
                        continue;
                    }

                    final String message = String.format(BROADCAST_MESSAGE_FORMAT, ipAddress, SERVER_PORT);
                    final byte[] data = message.getBytes();

                    // 广播到该接口的广播地址
                    final InetAddress broadcastAddress = getBroadcastAddress(networkInterface);
                    if (broadcastAddress != null) {
                        final DatagramPacket packet = new DatagramPacket(data, data.length,
                                broadcastAddress, BROADCAST_PORT);
                        socket.send(packet);
                        LOG.debug("广播服务器地址: {} -> {}", ipAddress, broadcastAddress);
                    }
                }
            }
        } catch (final IOException e) {
            LOG.error("广播失败", e);
        }
    }

    /**
     * 判断是否为虚拟网络地址
     *
     * @param ipAddress IP地址
     * @return true表示虚拟网络地址，应跳过
     */
    private boolean isVirtualNetworkAddress(final String ipAddress) {
        // Radmin VPN地址段：26.0.0.0/8
        if (ipAddress.startsWith("26.")) {
            return true;
        }

        // Hyper-V/Docker私有网络：172.16-31.0.0/12
        if (ipAddress.startsWith("172.")) {
            final String[] parts = ipAddress.split("\\.");
            if (parts.length >= 2) {
                try {
                    final int secondOctet = Integer.parseInt(parts[1]);
                    if (secondOctet >= 16 && secondOctet <= 31) {
                        return true;
                    }
                } catch (final NumberFormatException e) {
                    // 解析失败说明 IP 格式异常，不属于 172.16-31 段，继续检查
                    LOG.debug("解析 IP 第二段失败: ip={}", ipAddress, e);
                }
            }
        }

        return false;
    }

    /**
     * 获取网络接口的广播地址
     *
     * @param networkInterface 网络接口
     * @return 广播地址，如果没有则返回null
     */
    private InetAddress getBroadcastAddress(final NetworkInterface networkInterface) {
        try {
            // 尝试获取接口的广播地址
            final Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                final InetAddress address = addresses.nextElement();
                if (!address.isLoopbackAddress() && !address.getHostAddress().contains(":")) {
                    // 构造广播地址（例如192.168.43.x -> 192.168.43.255）
                    final String ip = address.getHostAddress();
                    final int lastDot = ip.lastIndexOf('.');
                    if (lastDot > 0) {
                        final String broadcastIp = ip.substring(0, lastDot + 1) + "255";
                        return InetAddress.getByName(broadcastIp);
                    }
                }
            }
        } catch (final IOException e) {
            LOG.debug("获取广播地址失败", e);
        }
        return null;
    }
}