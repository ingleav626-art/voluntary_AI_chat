package com.voluntary.chat.server.service;

import com.voluntary.chat.server.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 在线状态服务
 *
 * <p>
 * 基于 Redis 维护用户在线状态，支持心跳续期、批量查询和自动过期。
 * </p>
 *
 * <p>
 * Key 设计：online:user:{userId} → "1" TTL: 30 秒
 * </p>
 *
 * <p>
 * Redis 不可用时自动降级，所有在线状态返回 false。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineStatusService {

    private static final String ONLINE_KEY_PREFIX = "online:user:";

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties cacheProperties;

    /**
     * 标记用户上线
     *
     * @param userId 用户ID
     */
    public void markOnline(Long userId) {
        try {
            long ttl = cacheProperties.getOnlineStatus().getTtl();
            redisTemplate.opsForValue().set(
                    buildKey(userId),
                    "1",
                    ttl,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，在线状态标记失败: userId={}", userId, e);
        }
    }

    /**
     * 心跳续期 — 刷新 TTL
     *
     * @param userId 用户ID
     */
    public void refreshHeartbeat(Long userId) {
        try {
            long ttl = cacheProperties.getOnlineStatus().getTtl();
            redisTemplate.expire(buildKey(userId), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，心跳续期失败: userId={}", userId, e);
        }
    }

    /**
     * 标记用户下线
     *
     * @param userId 用户ID
     */
    public void markOffline(Long userId) {
        try {
            redisTemplate.delete(buildKey(userId));
        } catch (Exception e) {
            log.warn("Redis 不可用，下线标记失败: userId={}", userId, e);
        }
    }

    /**
     * 判断用户是否在线
     *
     * @param userId 用户ID
     * @return true 在线；false 离线或 Redis 不可用
     */
    public boolean isOnline(Long userId) {
        try {
            String value = redisTemplate.opsForValue().get(buildKey(userId));
            return "1".equals(value);
        } catch (Exception e) {
            log.warn("Redis 不可用，在线状态查询降级: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 批量查询在线状态
     *
     * @param userIds 用户ID列表
     * @return 在线用户ID集合
     */
    public Set<Long> filterOnline(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        try {
            List<String> keys = userIds.stream()
                    .map(this::buildKey)
                    .collect(Collectors.toList());
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Set.of();
            }
            // 与 userIds 一一对应，值为 "1" 的表示在线
            List<Long> userIdList = userIds.stream().collect(Collectors.toList());
            return java.util.stream.IntStream.range(0, values.size())
                    .filter(i -> "1".equals(values.get(i)))
                    .mapToObj(userIdList::get)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis 不可用，批量在线查询降级", e);
            return Set.of();
        }
    }

    /**
     * 批量查询在线状态（返回 List 的 boolean）
     *
     * @param userIds 用户ID列表
     * @return 每个用户对应的在线状态（与入参顺序一致）
     */
    public List<Boolean> batchCheckOnline(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        try {
            List<String> keys = userIds.stream()
                    .map(this::buildKey)
                    .collect(Collectors.toList());
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return userIds.stream().map(u -> false).collect(Collectors.toList());
            }
            return values.stream()
                    .map("1"::equals)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Redis 不可用，批量在线查询降级", e);
            return userIds.stream().map(u -> false).collect(Collectors.toList());
        }
    }

    private String buildKey(Long userId) {
        return ONLINE_KEY_PREFIX + userId;
    }
}