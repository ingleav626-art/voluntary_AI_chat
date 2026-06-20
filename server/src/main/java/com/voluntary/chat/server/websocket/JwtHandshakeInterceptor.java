package com.voluntary.chat.server.websocket;

import com.voluntary.chat.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 从 URL 参数中提取 JWT Token 并验证，将 userId 存入 attributes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String TOKEN_PARAM = "token";
    public static final String USER_ID_KEY = "userId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket 握手失败：Token 无效");
            return false;
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        attributes.put(USER_ID_KEY, userId);
        log.info("WebSocket 握手成功: userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private String extractToken(ServerHttpRequest request) {
        URI uri = request.getURI();
        return UriComponentsBuilder.fromUri(uri).build()
                .getQueryParams()
                .getFirst(TOKEN_PARAM);
    }
}
