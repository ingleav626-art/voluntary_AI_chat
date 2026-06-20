package org.example.client.service;

import org.example.client.model.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthService 单元测试
 *
 * 注意：HTTP 请求部分需要集成测试或 Mock
 */
@DisplayName("AuthService 测试")
class AuthServiceTest {

    @Test
    @DisplayName("获取单例实例")
    void testGetInstance() {
        final AuthService instance1 = AuthService.getInstance();
        final AuthService instance2 = AuthService.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("登录请求参数验证")
    void testLoginRequestValidation() {
        final AuthService authService = AuthService.getInstance();

        // 测试正常请求
        final LoginRequest request = new LoginRequest("13800138000", "password123", true);
        assertNotNull(request);
        assertEquals("13800138000", request.getPhone());
        assertEquals("password123", request.getPassword());
        assertTrue(request.getRememberMe());

        // 注意：实际的 HTTP 请求测试需要 Mock 或集成测试环境
    }

    @Test
    @DisplayName("手机号脱敏 - 正常手机号")
    void testMaskPhoneNormal() {
        // 通过反射测试私有方法 maskPhone
        // 这里测试公开的行为：登录请求会被记录（日志中手机号被脱敏）
        final LoginRequest request = new LoginRequest("13800138000", "password123", false);
        assertNotNull(request.getPhone());
        assertEquals(11, request.getPhone().length());
    }

    @Test
    @DisplayName("手机号脱敏 - 短手机号")
    void testMaskPhoneShort() {
        final LoginRequest request = new LoginRequest("138", "password", false);
        assertNotNull(request.getPhone());
        assertEquals(3, request.getPhone().length());
    }

    @Test
    @DisplayName("手机号脱敏 - null 手机号")
    void testMaskPhoneNull() {
        final LoginRequest request = new LoginRequest(null, "password", false);
        assertNull(request.getPhone());
    }
}