package com.voluntary.chat.server.config.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySmsCodeStorage 单元测试")
class MemorySmsCodeStorageTest {

    private MemorySmsCodeStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MemorySmsCodeStorage();
    }

    @Test
    @DisplayName("存储和获取验证码")
    void putAndGet() {
        storage.put("sms:code:13800138000", "123456", 5);
        assertEquals("123456", storage.get("sms:code:13800138000"));
    }

    @Test
    @DisplayName("删除验证码")
    void delete() {
        storage.put("sms:code:13800138000", "123456", 5);
        storage.delete("sms:code:13800138000");
        assertNull(storage.get("sms:code:13800138000"));
    }

    @Test
    @DisplayName("获取不存在的验证码返回 null")
    void getNonExistingReturnsNull() {
        assertNull(storage.get("sms:code:nonexist"));
    }

    @Test
    @DisplayName("覆盖已存在的验证码")
    void overwriteExisting() {
        storage.put("sms:code:13800138000", "111111", 5);
        storage.put("sms:code:13800138000", "222222", 5);
        assertEquals("222222", storage.get("sms:code:13800138000"));
    }
}
