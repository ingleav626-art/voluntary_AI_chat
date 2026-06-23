package com.voluntary.chat.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RateLimitingFilter 单元测试")
class RateLimitingFilterTest {

    // ==================== TokenBucket 单元测试 ====================

    @Test
    @DisplayName("新建令牌桶应满容量")
    void tokenBucket_shouldBeFull_whenCreated() {
        RateLimitingFilter bucket = new RateLimitingFilter();
        RateLimitingFilter.TokenBucket tb = new RateLimitingFilter.TokenBucket(10, 5);
        assertEquals(10, tb.availableTokens());
    }

    @Test
    @DisplayName("消耗令牌应减少可用令牌数")
    void tryConsume_shouldDecreaseTokens() {
        RateLimitingFilter.TokenBucket tb = new RateLimitingFilter.TokenBucket(10, 5);
        assertTrue(tb.tryConsume());
        assertEquals(9, tb.availableTokens());
    }

    @Test
    @DisplayName("令牌耗尽后应返回false")
    void tryConsume_shouldReturnFalse_whenExhausted() {
        RateLimitingFilter.TokenBucket tb = new RateLimitingFilter.TokenBucket(2, 5);
        assertTrue(tb.tryConsume());
        assertTrue(tb.tryConsume());
        assertFalse(tb.tryConsume());
    }

    @Test
    @DisplayName("超过容量的令牌不应被补充")
    void refill_shouldNotExceedCapacity() {
        RateLimitingFilter.TokenBucket tb = new RateLimitingFilter.TokenBucket(5, 10);

        // 消耗所有令牌
        for (int i = 0; i < 5; i++) {
            tb.tryConsume();
        }
        assertEquals(0, tb.availableTokens());

        // 模拟时间过去2秒，补充20个令牌（但上限是5）
        try {
            Thread.sleep(2100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(5, tb.availableTokens());
    }

    @Test
    @DisplayName("每秒最多补充一次令牌")
    void refill_shouldOnlyRefillOncePerSecond() {
        RateLimitingFilter.TokenBucket tb = new RateLimitingFilter.TokenBucket(10, 5);

        // 消耗到0
        for (int i = 0; i < 10; i++) {
            tb.tryConsume();
        }

        // 等待1.5秒，应补充一次（5个）
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(5, tb.availableTokens());
    }

    // ==================== 过滤器单元测试 ====================

    @Test
    @DisplayName("禁用限流时所有请求放行")
    void doFilterInternal_shouldPassThrough_whenDisabled() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "enabled", false);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/user/profile");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("白名单路径（/ws）不限流")
    void doFilterInternal_shouldNotRateLimit_whenWhitelistPath() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/ws");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("白名单路径（/files/）不限流")
    void doFilterInternal_shouldNotRateLimit_whenFilesPath() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/files/images/1.jpg");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("超限请求返回429")
    void doFilterInternal_shouldReturn429_whenExceeded() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        // 容量=2（最多2次突发）
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "capacity", 2);
        ReflectionTestUtils.setField(filter, "refillRate", 1);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(request.getRequestURI()).thenReturn("/api/user/profile");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getWriter()).thenReturn(printWriter);

        // 前2次正常
        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);
        verify(chain, times(2)).doFilter(request, response);

        // 第3次超限
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(429);
        assertTrue(stringWriter.toString().contains("请求过于频繁"));
    }

    @Test
    @DisplayName("不同IP的限流互不影响")
    void doFilterInternal_shouldTrackIpIndependently() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "capacity", 1);
        ReflectionTestUtils.setField(filter, "refillRate", 5);

        HttpServletRequest request1 = mock(HttpServletRequest.class);
        HttpServletRequest request2 = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(request1.getRequestURI()).thenReturn("/api/user/profile");
        when(request1.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request1.getRemoteAddr()).thenReturn("10.0.0.1");

        when(request2.getRequestURI()).thenReturn("/api/user/profile");
        when(request2.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request2.getRemoteAddr()).thenReturn("10.0.0.2");

        when(response.getWriter()).thenReturn(printWriter);

        // IP1 消耗完
        filter.doFilterInternal(request1, response, chain);
        // IP1 第2次超限
        filter.doFilterInternal(request1, response, chain);
        verify(response).setStatus(429);

        // IP2 正常
        filter.doFilterInternal(request2, response, chain);
        // IP2 再请求1次也超限
        filter.doFilterInternal(request2, response, chain);
        verify(response, times(2)).setStatus(429);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    @DisplayName("X-Forwarded-For 获取真实IP")
    void resolveClientIp_shouldUseXForwardedFor_whenAvailable() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "capacity", 10);
        ReflectionTestUtils.setField(filter, "refillRate", 10);

        // 模拟Nginx代理：X-Forwarded-For 有多级代理IP
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(request.getRequestURI()).thenReturn("/api/user/profile");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(response.getWriter()).thenReturn(printWriter);

        // 第一次 - 使用 X-Forwarded-For (203.0.113.1)
        filter.doFilterInternal(request, response, chain);

        // 消耗完203.0.113.1的令牌
        for (int i = 0; i < 9; i++) {
            filter.doFilterInternal(request, response, chain);
        }

        // 第11次，203.0.113.1超限
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("令牌桶并发安全")
    void tryConsume_shouldBeThreadSafe() throws Exception {
        RateLimitingFilter.TokenBucket tb = new RateLimitingFilter.TokenBucket(100, 100);

        // 并发消耗所有令牌
        int threadCount = 10;
        int requestsPerThread = 10;
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    if (tb.tryConsume()) {
                        successCount.incrementAndGet();
                    }
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        // 总消耗不超过容量
        assertTrue(successCount.get() <= 100);
        assertEquals(100 - successCount.get(), tb.availableTokens());
    }
}