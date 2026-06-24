package org.example.client.service;

import org.example.client.model.ForgotPasswordRequest;
import org.example.client.model.LoginRequest;
import org.example.client.model.LoginResponse;
import org.example.client.model.RefreshTokenRequest;
import org.example.client.model.RegisterRequest;
import org.example.client.model.SmsSendRequest;
import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthService 单元测试
 *
 * 注意：HTTP 请求部分需要集成测试或 Mock
 */
@DisplayName("AuthService 测试")
class AuthServiceTest {

    @BeforeEach
    void setUp() {
        TokenStorage.clear();
    }

    @AfterEach
    void tearDown() {
        TokenStorage.clear();
    }

    /**
     * 模拟登录状态（仅内存缓存）
     */
    private void simulateLogin() {
        final UserInfo user = new UserInfo();
        user.setUserId(1001L);
        user.setUsername("测试用户");
        final LoginResponse loginResponse = new LoginResponse(
                "test-access-token", "test-refresh-token", 7200L, user);
        TokenStorage.save(loginResponse, false);
    }

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
    void testMaskPhoneNormal() throws Exception {
        final String result = invokeMaskPhone("13800138000");
        assertEquals("138****8000", result);
    }

    @Test
    @DisplayName("手机号脱敏 - 短手机号返回 ***")
    void testMaskPhoneShort() throws Exception {
        final String result = invokeMaskPhone("138");
        assertEquals("***", result);
    }

    @Test
    @DisplayName("手机号脱敏 - null 手机号返回 ***")
    void testMaskPhoneNull() throws Exception {
        final String result = invokeMaskPhone(null);
        assertEquals("***", result);
    }

    @Test
    @DisplayName("手机号脱敏 - 长度刚好为7的边界")
    void testMaskPhoneBoundary() throws Exception {
        final String result = invokeMaskPhone("1234567");
        assertEquals("123****4567", result);
    }

    @Test
    @DisplayName("手机号脱敏 - 长度为6的边界返回 ***")
    void testMaskPhoneBelowBoundary() throws Exception {
        final String result = invokeMaskPhone("123456");
        assertEquals("***", result);
    }

    @Test
    @DisplayName("手机号脱敏 - 空字符串返回 ***")
    void testMaskPhoneEmpty() throws Exception {
        final String result = invokeMaskPhone("");
        assertEquals("***", result);
    }

    @Test
    @DisplayName("检查登录状态 - 未登录返回 false")
    void testIsLoggedIn_whenNoToken() {
        final AuthService authService = AuthService.getInstance();
        final boolean result = authService.isLoggedIn();
        assertFalse(result);
    }

    @Test
    @DisplayName("检查登录状态 - 已登录返回 true")
    void testIsLoggedIn_whenLoggedIn() {
        simulateLogin();
        final AuthService authService = AuthService.getInstance();
        final boolean result = authService.isLoggedIn();
        assertTrue(result);
    }

    @Test
    @DisplayName("登录-请求发送（预期网络失败）")
    void testLogin_shouldSendRequest() {
        final AuthService authService = AuthService.getInstance();
        final LoginRequest request = new LoginRequest("13800138000", "password123", false);

        final var response = authService.login(request).join();

        assertNotNull(response);
        // 没有真实服务器，预期返回错误响应
        assertNotNull(response.getCode());
    }

    @Test
    @DisplayName("注册-请求发送（预期网络失败）")
    void testRegister_shouldSendRequest() {
        final AuthService authService = AuthService.getInstance();
        final RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setUsername("测试用户");
        request.setPassword("password123");

        final var response = authService.register(request).join();

        assertNotNull(response);
        assertNotNull(response.getCode());
    }

    @Test
    @DisplayName("发送验证码-请求发送（预期网络失败）")
    void testSendSmsCode_shouldSendRequest() {
        final AuthService authService = AuthService.getInstance();
        final SmsSendRequest request = new SmsSendRequest("13800138000");

        final var response = authService.sendSmsCode(request).join();

        assertNotNull(response);
        assertNotNull(response.getCode());
    }

    @Test
    @DisplayName("刷新令牌-请求发送（预期网络失败）")
    void testRefreshToken_shouldSendRequest() {
        final AuthService authService = AuthService.getInstance();
        final RefreshTokenRequest request = new RefreshTokenRequest("test-refresh-token");

        final var response = authService.refreshToken(request).join();

        assertNotNull(response);
        assertNotNull(response.getCode());
    }

    @Test
    @DisplayName("忘记密码-请求发送（预期网络失败）")
    void testForgotPassword_shouldSendRequest() {
        final AuthService authService = AuthService.getInstance();
        final ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setNewPassword("newpassword123");
        request.setConfirmPassword("newpassword123");

        final var response = authService.forgotPassword(request).join();

        assertNotNull(response);
        assertNotNull(response.getCode());
    }

    /**
     * 通过反射调用私有方法 maskPhone
     */
    private String invokeMaskPhone(final String phone) throws Exception {
        final AuthService authService = AuthService.getInstance();
        final Method method = AuthService.class.getDeclaredMethod("maskPhone", String.class);
        method.setAccessible(true);
        return (String) method.invoke(authService, phone);
    }
}

