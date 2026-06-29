package com.voluntary.chat.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * 当 Redis 可用时使用 Redis 计数实现多实例共享的分布式限流，
 * Redis 不可用时自动降级为本地令牌桶。
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
 *   redis-enabled: true # 使用 Redis 分布式限流
 * </pre>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitingFilter.class);

    /** 本地令牌桶容器（Redis 降级时使用） */
    private final Map<String, TokenBucket> localBuckets = new ConcurrentHashMap<>();

    /** 是否启用限流（云端模式启用） */
    @Value("${rate-limit.enabled:false}")
    private boolean enabled;

    /** 令牌桶容量（最大突发请求数） */
    @Value("${rate-limit.capacity:20}")
    private int capacity;

    /** 每秒补充令牌数 */
    @Value("${rate-limit.refill-rate:10}")
    private int refillRate;

    /** 是否使用 Redis 分布式限流 */
    @Value("${rate-limit.redis-enabled:false}")
    private boolean redisEnabled;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    /** Redis 滑动窗口大小（秒） */
    private static final long REDIS_WINDOW_SECONDS = 1;
    /** HTTP 429 状态码 */
    private static final int TOO_MANY_REQUESTS = 429;

    /** 白名单路径（不限流） */
    private static final String[] WHITELIST_PATHS = {
            "/ws", // WebSocket 握手
            "/files/" // 静态文件
    };

    public RateLimitingFilter() {
    }

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

        boolean allowed;
        if (redisEnabled) {
            allowed = tryConsumeRedis(clientIp, path);
        } else {
            allowed = tryConsumeLocal(clientIp);
        }

        if (allowed) {
            LOG.debug("请求放行: ip={}, path={}", clientIp, path);
            filterChain.doFilter(request, response);
        } else {
            LOG.warn("请求被限流: ip={}, path={}", clientIp, path);
            response.setStatus(TOO_MANY_REQUESTS);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}");
        }
    }

    /**
     * 使用 Redis 分布式限流（滑动窗口计数器）
     *
     * <p>
     * Key: {@code rate_limit:{path}:{ip}}，TTL: 1秒。
     * 每秒允许的请求数不超过 capacity。
     * </p>
     */
    private boolean tryConsumeRedis(String clientIp, String path) {
        try {
            String endpoint = path.replaceAll("/api/", "").replaceAll("/\\d+", "/{id}");
            String key = RATE_LIMIT_PREFIX + endpoint + ":" + clientIp;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                count = 1L;
            }
            if (count == 1) {
                redisTemplate.expire(key, REDIS_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            return count <= capacity;
        } catch (Exception e) {
            LOG.warn("Redis 限流失败，降级为本地限流: ip={}", clientIp, e);
            return tryConsumeLocal(clientIp);
        }
    }

    /**
     * 使用本地令牌桶限流（Redis 降级方案）
     */
    private boolean tryConsumeLocal(String clientIp) {
        final TokenBucket bucket = localBuckets.computeIfAbsent(clientIp,
                k -> new TokenBucket(capacity, refillRate));
        return bucket.tryConsume();
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
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * 令牌桶（本地限流用）
     *
     * <p>
     * 基于时间戳的令牌补充算法，线程安全。
     * </p>
     */
    static class TokenBucket {
        private final AtomicLong tokens;
        private final int capacity;
        private final int refillRate;
        private final AtomicLong lastRefillNanos;

        TokenBucket(final int capacity, final int refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

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

        long availableTokens() {
            refill();
            return Math.max(0, tokens.get());
        }

        private void refill() {
            final long now = System.nanoTime();
            final long last = lastRefillNanos.get();
            final long elapsed = now - last;

            if (elapsed < TimeUnit.SECONDS.toNanos(1)) {
                return;
            }

            if (!lastRefillNanos.compareAndSet(last, now)) {
                return;
            }

            long current = tokens.get();
            long newTokens = Math.min(capacity, current + refillRate * (elapsed / TimeUnit.SECONDS.toNanos(1)));
            tokens.set(newTokens);
        }
    }
}