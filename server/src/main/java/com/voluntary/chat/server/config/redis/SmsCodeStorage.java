package com.voluntary.chat.server.config.redis;

public interface SmsCodeStorage {

    void put(String key, String value, long ttlMinutes);

    String get(String key);

    void delete(String key);
}
