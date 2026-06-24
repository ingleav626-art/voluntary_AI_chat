package com.voluntary.chat.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.voluntary.chat.server.service.ServerBroadcastService;

/**
 * 服务器启动监听器
 *
 * <p>仅在热点模式下自动启动广播服务地址。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Component
public class ServerStartupRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ServerStartupRunner.class);

    private final ServerBroadcastService broadcastService;

    /** 是否启用广播服务（仅热点模式） */
    @Value("${server.broadcast.enabled:false}")
    private boolean broadcastEnabled;

    public ServerStartupRunner(final ServerBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @Override
    public void run(final ApplicationArguments args) {
        if (broadcastEnabled) {
            LOG.info("热点模式：服务器启动完成，开始广播服务地址");
            broadcastService.startBroadcast();
        } else {
            LOG.info("非热点模式：服务器启动完成，不启动广播服务");
        }
    }
}