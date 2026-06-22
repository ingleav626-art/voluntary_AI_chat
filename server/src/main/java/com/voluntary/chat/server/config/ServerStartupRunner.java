package com.voluntary.chat.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.voluntary.chat.server.service.ServerBroadcastService;

/**
 * 服务器启动监听器
 *
 * <p>服务器启动后自动开始广播服务地址。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Component
public class ServerStartupRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ServerStartupRunner.class);

    private final ServerBroadcastService broadcastService;

    public ServerStartupRunner(final ServerBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @Override
    public void run(final ApplicationArguments args) {
        LOG.info("服务器启动完成，开始广播服务地址");
        broadcastService.startBroadcast();
    }
}