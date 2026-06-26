package org.example.client.server.config;

import com.voluntary.chat.server.websocket.AiWebSocketHandler;
import com.voluntary.chat.server.websocket.JwtHandshakeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 客户包 WebSocket 配置
 *
 * <p>
 * 注册 {@link AiWebSocketHandler} 处理 AI 对话 WebSocket 连接。
 * 客户包不需要真人消息转发，因此使用精简版 WebSocket 配置。
 * </p>
 */
@Configuration
@EnableWebSocket
public class ClientWebSocketConfig implements WebSocketConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(ClientWebSocketConfig.class);

    private final AiWebSocketHandler aiWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public ClientWebSocketConfig(final AiWebSocketHandler aiWebSocketHandler,
            final JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.aiWebSocketHandler = aiWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry.addHandler(aiWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
        LOG.info("客户包 WebSocket 配置完成: /ws -> AiWebSocketHandler");
    }
}
