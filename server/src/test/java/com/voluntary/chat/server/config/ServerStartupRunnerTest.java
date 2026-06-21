package com.voluntary.chat.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.voluntary.chat.server.service.ServerBroadcastService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;

/**
 * ServerStartupRunner 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ServerStartupRunnerTest {

    @Mock
    private ServerBroadcastService broadcastService;

    @Test
    @DisplayName("ServerStartupRunner应正确创建")
    void runnerShouldBeCreated() {
        final ServerStartupRunner runner = new ServerStartupRunner(broadcastService);
        assertNotNull(runner);
    }

    @Test
    @DisplayName("run方法应调用广播服务启动")
    void runShouldStartBroadcast() {
        doNothing().when(broadcastService).startBroadcast();
        final ServerStartupRunner runner = new ServerStartupRunner(broadcastService);
        runner.run(null);
        // 验证broadcastService.startBroadcast()被调用（通过Mockito verify）
        org.mockito.Mockito.verify(broadcastService).startBroadcast();
    }
}