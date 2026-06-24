package com.voluntary.chat.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求限流过滤器
 *
 * <p>
 * 基于令牌桶算法实现IP级别的请求限流，防止暴力攻击和DDoS。
 * 云端模式下启用，本地/热点模式默认关闭。
 * </p>
 *
 * <p>
 * 配置示例（application-cloud.yml）：
 * </p>
 * 
 * <pre>
 * rate-limit:
 *   enabled: true
 *   capacity: 20       # 令牌桶容量
 *   refill-rate: 10    # 每秒补充的令牌数
 * </pre>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitingFilter.class);

    /** 令牌桶容器 */
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** 是否启用限流（云端模式启用） */
    @Value("${rate-limit.enabled:false}")
    private boolean enabled;

    /** 令牌桶容量（最大突发请求数） */
    @Value("${rate-limit.capacity:20}")
    private int capacity;

    /** 每秒补充令牌数 */
    @Value("${rate-limit.refill-rate:10}")
    private int refillRate;

    /** HTTP 429 状态码 */
    private static final int TOO_MANY_REQUESTS = 429;

    /** 白名单路径（不限流） */
    private static final String[] WHITELIST_PATHS = {
            "/ws", // WebSocket 握手
            "/files/" // 静态文件
    };

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain)
            throws ServletException, IOException {

        // 未启用限流，直接放行
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // 白名单路径不限流
        final String path = request.getRequestURI();
        for (final String whitelist : WHITELIST_PATHS) {
            if (path.startsWith(whitelist)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 获取客户端IP
        final String clientIp = resolveClientIp(request);
        final TokenBucket bucket = buckets.computeIfAbsent(clientIp,
                k -> new TokenBucket(capacity, refillRate));

        if (bucket.tryConsume()) {
            // 未超限，放行
            LOG.debug("请求放行: ip={}, path={}", clientIp, path);
            filterChain.doFilter(request, response);
        } else {
            // 超限，返回429
            LOG.warn("请求被限流: ip={}, path={}, bucket={}/{}",
                    clientIp, path, bucket.availableTokens(), capacity);
            response.setStatus(TOO_MANY_REQUESTS);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}");
        }
    }

    /**
     * 解析客户端真实IP
     *
     * <p>
     * 优先取 X-Forwarded-For（Nginx代理），再取 RemoteAddr。
     * </p>
     */
    private String resolveClientIp(final HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能有多级代理，取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * 令牌桶
     *
     * <p>
     * 基于时间戳的令牌补充算法，线程安全。
     * </p>
     */
    static class TokenBucket {
        /** 当前可用令牌数（最多到capacity） */
        private final AtomicLong tokens;
        /** 令牌桶容量 */
        private final int capacity;
        /** 每秒补充令牌数 */
        private final int refillRate;
        /** 上次补充时间戳（纳秒） */
        private final AtomicLong lastRefillNanos;

        TokenBucket(final int capacity, final int refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

        /**
         * 尝试消耗一个令牌
         *
         * @return true 如果成功消耗，false 如果令牌不足
         */
        boolean tryConsume() {
            refill();
            while (true) {
                final long current = tokens.get();
                if (current <= 0) {
                    return false;
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        /**
         * 获取当前可用令牌数
         */
        long availableTokens() {
            refill();
            return Math.max(0, tokens.get());
        }

        /**
         * 根据时间差补充令牌
         */
        private void refill() {
            final long now = System.nanoTime();
            final long last = lastRefillNanos.get();
            final long elapsed = now - last;

            // 每秒钟最多补充一次
            if (elapsed < TimeUnit.SECONDS.toNanos(1)) {
                return;
            }

            // CAS更新上次补充时间，防止多线程重复补充
            if (!lastRefillNanos.compareAndSet(last, now)) {
                return;
            }

            // 计算应补充的令牌数
            final long seconds = elapsed / TimeUnit.SECONDS.toNanos(1);
            final long toAdd = seconds * refillRate;

            if (toAdd > 0) {
                tokens.updateAndGet(current -> Math.min(capacity, current + toAdd));
            }
        }
    }
}