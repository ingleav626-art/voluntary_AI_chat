package com.voluntary.chat.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存配置属性
 *
 * <p>对应配置前缀 app.cache，控制各缓存项的 TTL 及开关。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    /** 全局缓存开关 */
    private boolean enabled = true;

    /** 会话缓存配置 */
    private CacheItem conversation = new CacheItem(86400);

    /** 在线状态配置 */
    private OnlineStatusConfig onlineStatus = new OnlineStatusConfig();

    /** 未读计数配置 */
    private CacheItem unread = new CacheItem(604800);

    /** 群成员缓存配置 */
    private CacheItem groupMembers = new CacheItem(0);

    /** AI 记忆缓存配置 */
    private CacheItem aiMemory = new CacheItem(1800);

    /** 离线消息配置 */
    private OfflineMessageConfig offlineMessage = new OfflineMessageConfig();

    @Data
    public static class CacheItem {
        /** TTL 秒数，0 表示永不过期 */
        private long ttl;

        public CacheItem() {
        }

        public CacheItem(long ttl) {
            this.ttl = ttl;
        }
    }

    @Data
    public static class OnlineStatusConfig {
        /** 在线状态 TTL 秒数 */
        private long ttl = 30;
        /** 心跳间隔秒数 */
        private long heartbeatInterval = 15;
    }

    @Data
    public static class OfflineMessageConfig {
        /** 离线队列最大长度 */
        private int maxSize = 1000;
    }
}
