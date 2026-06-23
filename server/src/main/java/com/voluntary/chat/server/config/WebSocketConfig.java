package com.voluntary.chat.server.config;

import com.voluntary.chat.server.websocket.ChatWebSocketHandler;
import com.voluntary.chat.server.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketConfig.class);

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /** 云端模式是否启用 */
    @Value("${cloud.mode.enabled:false}")
    private boolean cloudModeEnabled;

    /** 允许的来源域名（云端模式生效） */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (cloudModeEnabled && !allowedOrigins.isBlank()) {
            // 云端模式：只允许配置的域名
            final String[] origins = allowedOrigins.split(",");
            registry.addHandler(chatWebSocketHandler, "/ws")
                    .addInterceptors(jwtHandshakeInterceptor)
                    .setAllowedOrigins(origins);
            LOG.info("云端模式WebSocket配置: 允许来源={}", List.of(origins));
        } else {
            // 本地/热点模式：允许所有来源
            registry.addHandler(chatWebSocketHandler, "/ws")
                    .addInterceptors(jwtHandshakeInterceptor)
                    .setAllowedOriginPatterns("*");
            LOG.info("非云端模式WebSocket配置: 允许所有来源");
        }
    }
}