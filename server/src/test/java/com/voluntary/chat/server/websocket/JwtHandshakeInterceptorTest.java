package com.voluntary.chat.server.websocket;

import com.voluntary.chat.server.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtHandshakeInterceptor 测试")
class JwtHandshakeInterceptorTest {

    private JwtHandshakeInterceptor interceptor;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private Map<String, Object> attributes;

    private static final Long USER_ID = 1001L;
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        interceptor = new JwtHandshakeInterceptor(jwtTokenProvider);
        attributes = new HashMap<>();
    }

    @Test
    @DisplayName("握手成功 - 有效 Token")
    void beforeHandshake_shouldSucceed_withValidToken() {
        URI uri = UriComponentsBuilder.fromPath("/ws")
                .queryParam("token", VALID_TOKEN)
                .build().toUri();
        when(request.getURI()).thenReturn(uri);
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(USER_ID);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(result);
        assertEquals(USER_ID, attributes.get(JwtHandshakeInterceptor.USER_ID_KEY));
        verify(jwtTokenProvider).validateToken(VALID_TOKEN);
        verify(jwtTokenProvider).getUserIdFromToken(VALID_TOKEN);
    }

    @Test
    @DisplayName("握手失败 - 缺少 Token")
    void beforeHandshake_shouldFail_withoutToken() {
        URI uri = UriComponentsBuilder.fromPath("/ws").build().toUri();
        when(request.getURI()).thenReturn(uri);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
        assertNull(attributes.get(JwtHandshakeInterceptor.USER_ID_KEY));
        verify(jwtTokenProvider, never()).validateToken(any());
    }

    @Test
    @DisplayName("握手失败 - Token 无效")
    void beforeHandshake_shouldFail_withInvalidToken() {
        URI uri = UriComponentsBuilder.fromPath("/ws")
                .queryParam("token", "invalid-token")
                .build().toUri();
        when(request.getURI()).thenReturn(uri);
        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
        assertNull(attributes.get(JwtHandshakeInterceptor.USER_ID_KEY));
    }

    @Test
    @DisplayName("afterHandshake 不抛异常")
    void afterHandshake_shouldNotThrow() {
        assertDoesNotThrow(() ->
                interceptor.afterHandshake(request, response, wsHandler, null));
    }
}
