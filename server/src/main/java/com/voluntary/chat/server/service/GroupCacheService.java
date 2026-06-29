package com.voluntary.chat.server.service;

import com.voluntary.chat.server.config.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 群成员缓存服务
 *
 * <p>缓存群成员列表，避免群消息广播时每次查询 group_member 表。</p>
 *
 * <p>Key 设计：</p>
 * <ul>
 *   <li>{@code group:members:{groupId}} → Set [userId1, userId2, ...] — 无 TTL（成员变更时更新）</li>
 *   <li>{@code group:member_count:{groupId}} → 数字 — 无 TTL</li>
 * </ul>
 *
 * <p>Redis 不可用时自动降级为查库。</p>
 */
@Slf4j
@Service
public class GroupCacheService {

    private static final String MEMBERS_PREFIX = "group:members:";
    private static final String MEMBER_COUNT_PREFIX = "group:member_count:";

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties cacheProperties;

    public GroupCacheService(StringRedisTemplate redisTemplate, CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 获取群成员 ID 列表（无序）
     *
     * @return 成员 ID 集合；缓存未命中或异常返回 null
     */
    public Set<Long> getMemberIds(Long groupId) {
        if (!cacheProperties.isEnabled()) {
            return null;
        }
        try {
            Set<String> members = redisTemplate.opsForSet().members(membersKey(groupId));
            if (members == null || members.isEmpty()) {
                return null;
            }
            return members.stream().map(Long::parseLong).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("读取群成员缓存失败: groupId={}", groupId, e);
            return null;
        }
    }

    /**
     * 缓存群成员列表
     */
    public void setMemberIds(Long groupId, List<Long> memberIds) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            String key = membersKey(groupId);
            redisTemplate.delete(key);
            if (memberIds != null && !memberIds.isEmpty()) {
                String[] ids = memberIds.stream().map(String::valueOf).toArray(String[]::new);
                redisTemplate.opsForSet().add(key, ids);
            }
        } catch (Exception e) {
            log.warn("缓存群成员失败: groupId={}", groupId, e);
        }
    }

    /**
     * 添加群成员
     */
    public void addMember(Long groupId, Long userId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForSet().add(membersKey(groupId), String.valueOf(userId));
            redisTemplate.opsForValue().increment(memberCountKey(groupId), 1);
        } catch (Exception e) {
            log.warn("添加群成员缓存失败: groupId={}, userId={}", groupId, userId, e);
        }
    }

    /**
     * 移除群成员
     */
    public void removeMember(Long groupId, Long userId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForSet().remove(membersKey(groupId), String.valueOf(userId));
            redisTemplate.opsForValue().decrement(memberCountKey(groupId), 1);
        } catch (Exception e) {
            log.warn("移除群成员缓存失败: groupId={}, userId={}", groupId, userId, e);
        }
    }

    /**
     * 获取群成员数量
     *
     * @return 成员数，缓存未命中返回 -1
     */
    public long getMemberCount(Long groupId) {
        if (!cacheProperties.isEnabled()) {
            return -1;
        }
        try {
            String value = redisTemplate.opsForValue().get(memberCountKey(groupId));
            if (value == null) {
                return -1;
            }
            return Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取群成员数量缓存失败: groupId={}", groupId, e);
            return -1;
        }
    }

    /**
     * 设置群成员数量
     */
    public void setMemberCount(Long groupId, long count) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(memberCountKey(groupId), String.valueOf(count));
        } catch (Exception e) {
            log.warn("缓存群成员数量失败: groupId={}", groupId, e);
        }
    }

    /**
     * 删除群组的所有缓存（群解散时调用）
     */
    public void deleteGroupCache(Long groupId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(membersKey(groupId));
            redisTemplate.delete(memberCountKey(groupId));
        } catch (Exception e) {
            log.warn("删除群缓存失败: groupId={}", groupId, e);
        }
    }

    private String membersKey(Long groupId) {
        return MEMBERS_PREFIX + groupId;
    }

    private String memberCountKey(Long groupId) {
        return MEMBER_COUNT_PREFIX + groupId;
    }
}
