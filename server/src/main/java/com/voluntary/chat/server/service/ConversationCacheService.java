package com.voluntary.chat.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.voluntary.chat.server.config.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 会话缓存服务
 *
 * <p>
 * 缓存会话的最后一条消息和未读计数，避免每次加载首页时产生 N+1 SQL 查询。
 * </p>
 *
 * <p>
 * Key 设计：
 * </p>
 * <ul>
 * <li>{@code conv:last_msg:{sessionId}} → JSON → TTL: 1 天</li>
 * <li>{@code conv:unread:{userId}:{sessionId}} → 数字 → TTL: 7 天</li>
 * <li>{@code conv:list:{userId}} → ZSet {sessionId: lastMsgTimestamp} → TTL: 1
 * 天</li>
 * </ul>
 *
 * <p>
 * Redis 不可用时自动降级，所有操作静默失败。
 * </p>
 */
@Slf4j
@Service
public class ConversationCacheService {

    private static final String LAST_MSG_PREFIX = "conv:last_msg:";
    private static final String UNREAD_PREFIX = "conv:unread:";
    private static final String LIST_PREFIX = "conv:list:";

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties cacheProperties;
    private final ObjectMapper objectMapper;

    public ConversationCacheService(StringRedisTemplate redisTemplate,
            CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ======================== 最后一条消息缓存 ========================

    /**
     * 缓存会话的最后一条消息
     */
    public void setLastMessage(String sessionId, LastMessageCache value) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            long ttl = cacheProperties.getConversation().getTtl();
            redisTemplate.opsForValue().set(
                    lastMsgKey(sessionId),
                    json,
                    ttl,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存最后消息失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 获取会话的最后一条消息缓存
     *
     * @return 缓存内容，未命中或异常返回 null
     */
    public LastMessageCache getLastMessage(String sessionId) {
        if (!cacheProperties.isEnabled()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(lastMsgKey(sessionId));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, LastMessageCache.class);
        } catch (Exception e) {
            log.warn("读取最后消息缓存失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 删除会话的最后消息缓存
     */
    public void deleteLastMessage(String sessionId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(lastMsgKey(sessionId));
        } catch (Exception e) {
            log.warn("删除最后消息缓存失败: sessionId={}", sessionId, e);
        }
    }

    // ======================== 未读计数 ========================

    /**
     * 增加未读数
     */
    public void incrementUnread(Long userId, String sessionId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            long ttl = cacheProperties.getUnread().getTtl();
            String key = unreadKey(userId, sessionId);
            redisTemplate.opsForValue().increment(key, 1);
            // increment 后设置 TTL（第一次设置有效，后续会刷新 TTL）
            redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("未读数增加失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 清零未读数
     */
    public void clearUnread(Long userId, String sessionId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            String key = unreadKey(userId, sessionId);
            redisTemplate.opsForValue().set(key, "0");
        } catch (Exception e) {
            log.warn("未读数清零失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 获取未读数
     *
     * @return 未读数，缓存未命中返回 -1
     */
    public long getUnread(Long userId, String sessionId) {
        if (!cacheProperties.isEnabled()) {
            return -1;
        }
        try {
            String value = redisTemplate.opsForValue().get(unreadKey(userId, sessionId));
            if (value == null) {
                return -1;
            }
            return Long.parseLong(value);
        } catch (Exception e) {
            log.warn("未读数查询失败: userId={}, sessionId={}", userId, sessionId, e);
            return -1;
        }
    }

    // ======================== 会话列表缓存（ZSet） ========================

    /**
     * 更新会话的最后时间（用于排序）
     */
    public void updateSessionList(Long userId, String sessionId, LocalDateTime lastMsgTime) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            long ttl = cacheProperties.getConversation().getTtl();
            String key = listKey(userId);
            long score = lastMsgTime == null
                    ? System.currentTimeMillis()
                    : java.time.LocalDateTime.from(lastMsgTime)
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            redisTemplate.opsForZSet().add(key, sessionId, score);
            redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("更新会话列表缓存失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 删除会话列表中的条目
     */
    public void removeSessionFromList(Long userId, String sessionId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForZSet().remove(listKey(userId), sessionId);
        } catch (Exception e) {
            log.warn("删除会话列表缓存条目失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    // ======================== 批量删除 ========================

    /**
     * 删除会话相关的所有缓存（清会话时调用）
     */
    public void deleteConversationCache(Long userId, String sessionId) {
        deleteLastMessage(sessionId);
        clearUnread(userId, sessionId);
        removeSessionFromList(userId, sessionId);
    }

    // ======================== Key 构造 ========================

    private String lastMsgKey(String sessionId) {
        return LAST_MSG_PREFIX + sessionId;
    }

    private String unreadKey(Long userId, String sessionId) {
        return UNREAD_PREFIX + userId + ":" + sessionId;
    }

    private String listKey(Long userId) {
        return LIST_PREFIX + userId;
    }

    // ======================== 缓存值对象 ========================

    /**
     * 最后一条消息的缓存值
     */
    public record LastMessageCache(
            String content,
            int type,
            LocalDateTime createTime,
            Long senderId) {
    }

    // ======================== 离线消息队列 ========================

    private static final String OFFLINE_MSG_PREFIX = "offline:msg:";

    /**
     * 存储离线消息
     *
     * <p>
     * LPUSH 到用户的离线消息列表，超出最大长度时从尾部截断（丢弃最旧的消息）。
     * </p>
     */
    public void pushOfflineMessage(Long userId, String messageJson) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            String key = offlineMsgKey(userId);
            redisTemplate.opsForList().leftPush(key, messageJson);
            int maxSize = cacheProperties.getOfflineMessage().getMaxSize();
            // 限制队列长度，超长从尾部截断
            redisTemplate.opsForList().trim(key, 0, maxSize - 1);
        } catch (Exception e) {
            log.warn("存储离线消息失败: userId={}", userId, e);
        }
    }

    /**
     * 弹出离线消息（按入队顺序消费，从尾部弹出）
     *
     * @return 消息 JSON，无消息时返回 null
     */
    public String popOfflineMessage(Long userId) {
        if (!cacheProperties.isEnabled()) {
            return null;
        }
        try {
            return redisTemplate.opsForList().rightPop(offlineMsgKey(userId));
        } catch (Exception e) {
            log.warn("弹出离线消息失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 获取离线消息数量
     */
    public long getOfflineMessageCount(Long userId) {
        if (!cacheProperties.isEnabled()) {
            return 0;
        }
        try {
            Long size = redisTemplate.opsForList().size(offlineMsgKey(userId));
            return size == null ? 0 : size;
        } catch (Exception e) {
            log.warn("查询离线消息数失败: userId={}", userId, e);
            return 0;
        }
    }

    /**
     * 清除用户的所有离线消息（上线后消费完毕后调用）
     */
    public void clearOfflineMessages(Long userId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(offlineMsgKey(userId));
        } catch (Exception e) {
            log.warn("清除离线消息失败: userId={}", userId, e);
        }
    }

    private String offlineMsgKey(Long userId) {
        return OFFLINE_MSG_PREFIX + userId;
    }
}