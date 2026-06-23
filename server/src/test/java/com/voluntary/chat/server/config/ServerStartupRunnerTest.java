package com.voluntary.chat.server.config;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.voluntary.chat.server.service.ServerBroadcastService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
    @DisplayName("run方法应在热点模式下调用广播服务启动")
    void runShouldStartBroadcastInHotspotMode() throws Exception {
        doNothing().when(broadcastService).startBroadcast();
        final ServerStartupRunner runner = new ServerStartupRunner(broadcastService);

        // 使用反射设置broadcastEnabled为true（热点模式）
        final Field field = ServerStartupRunner.class.getDeclaredField("broadcastEnabled");
        field.setAccessible(true);
        field.set(runner, true);

        runner.run(null);

        // 验证broadcastService.startBroadcast()被调用
        verify(broadcastService).startBroadcast();
    }

    @Test
    @DisplayName("run方法应在非热点模式下不调用广播服务启动")
    void runShouldNotStartBroadcastInNonHotspotMode() throws Exception {
        final ServerStartupRunner runner = new ServerStartupRunner(broadcastService);

        // 使用反射设置broadcastEnabled为false（非热点模式）
        final Field field = ServerStartupRunner.class.getDeclaredField("broadcastEnabled");
        field.setAccessible(true);
        field.set(runner, false);

        runner.run(null);

        // 验证broadcastService.startBroadcast()未被调用
        verify(broadcastService, never()).startBroadcast();
    }
}