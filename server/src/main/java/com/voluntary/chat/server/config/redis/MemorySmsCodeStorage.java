package com.voluntary.chat.server.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnMissingBean(RedisSmsCodeStorage.class)
public class MemorySmsCodeStorage implements SmsCodeStorage {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, long ttlMinutes) {
        store.put(key, value);
        log.warn("Redis 不可用，验证码存储在内存中（重启后丢失）: key={}", key);
    }

    @Override
    public String get(String key) {
        return store.get(key);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }
}
