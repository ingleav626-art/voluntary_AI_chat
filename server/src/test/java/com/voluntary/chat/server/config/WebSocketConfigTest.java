package com.voluntary.chat.server.config;

import com.voluntary.chat.server.websocket.ChatWebSocketHandler;
import com.voluntary.chat.server.websocket.JwtHandshakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("WebSocketConfig 单元测试")
class WebSocketConfigTest {

    private ChatWebSocketHandler chatHandler;
    private JwtHandshakeInterceptor interceptor;
    private WebSocketConfig config;

    @BeforeEach
    void setUp() {
        chatHandler = mock(ChatWebSocketHandler.class);
        interceptor = mock(JwtHandshakeInterceptor.class);
        config = new WebSocketConfig(chatHandler, interceptor);
    }

    @Test
    @DisplayName("构造 WebSocketConfig")
    void shouldInstantiate() {
        assertNotNull(config);
    }

    @Test
    @DisplayName("非云端模式 - registerWebSocketHandlers 调用 setAllowedOriginPatterns")
    void registerWebSocketHandlers_shouldUseAllowedOriginPatterns_whenNotCloudMode() {
        // 非云端模式
        ReflectionTestUtils.setField(config, "cloudModeEnabled", false);

        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);

        when(registry.addHandler(any(), anyString())).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(chatHandler, "/ws");
        verify(registration).addInterceptors(interceptor);
        // 非云端调用 setAllowedOriginPatterns
        verify(registration).setAllowedOriginPatterns("*");
    }

    @Test
    @DisplayName("云端模式 - registerWebSocketHandlers 调用 setAllowedOrigins")
    void registerWebSocketHandlers_shouldUseAllowedOrigins_whenCloudMode() {
        // 云端模式
        ReflectionTestUtils.setField(config, "cloudModeEnabled", true);
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://chat.example.com");

        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);

        when(registry.addHandler(any(), anyString())).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.setAllowedOrigins(any())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(chatHandler, "/ws");
        verify(registration).addInterceptors(interceptor);
        // 云端调用 setAllowedOrigins（限制域名）
        verify(registration).setAllowedOrigins("https://chat.example.com");
    }

    @Test
    @DisplayName("云端模式但未配置域名 - registerWebSocketHandlers 使用 setAllowedOriginPatterns")
    void registerWebSocketHandlers_shouldUseFallback_whenCloudModeWithoutOrigins() {
        // 云端模式但未配置 allowedOrigins
        ReflectionTestUtils.setField(config, "cloudModeEnabled", true);
        ReflectionTestUtils.setField(config, "allowedOrigins", "");

        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);

        when(registry.addHandler(any(), anyString())).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(chatHandler, "/ws");
        verify(registration).addInterceptors(interceptor);
        // allowedOrigins 为空时退化为允许所有来源
        verify(registration).setAllowedOriginPatterns("*");
    }
}