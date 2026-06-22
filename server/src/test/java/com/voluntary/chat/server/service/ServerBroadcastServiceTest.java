package com.voluntary.chat.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ServerBroadcastService 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ServerBroadcastServiceTest {

    private ServerBroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new ServerBroadcastService();
    }

    @Test
    @DisplayName("启动广播服务不应抛出异常")
    void startBroadcastShouldNotThrow() {
        assertDoesNotThrow(() -> broadcastService.startBroadcast());
        // 清理
        broadcastService.stopBroadcast();
    }

    @Test
    @DisplayName("停止广播服务不应抛出异常")
    void stopBroadcastShouldNotThrow() {
        broadcastService.startBroadcast();
        assertDoesNotThrow(() -> broadcastService.stopBroadcast());
    }

    @Test
    @DisplayName("重复启动广播服务不应抛出异常")
    void startBroadcastTwiceShouldNotThrow() {
        broadcastService.startBroadcast();
        assertDoesNotThrow(() -> broadcastService.startBroadcast());
        broadcastService.stopBroadcast();
    }

    @Test
    @DisplayName("未启动时停止广播服务不应抛出异常")
    void stopBroadcastWithoutStartShouldNotThrow() {
        assertDoesNotThrow(() -> broadcastService.stopBroadcast());
    }

    @Test
    @DisplayName("广播服务实例应正确创建")
    void broadcastServiceShouldBeCreated() {
        assertNotNull(broadcastService);
    }

    @Test
    @DisplayName("启动后停止再启动应正常工作")
    void restartBroadcastShouldWork() {
        broadcastService.startBroadcast();
        broadcastService.stopBroadcast();
        assertDoesNotThrow(() -> broadcastService.startBroadcast());
        broadcastService.stopBroadcast();
    }
}