package org.example.client.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据模型测试
 */
@DisplayName("数据模型测试")
class ModelTest {

    @Test
    @DisplayName("LoginRequest 构造和 Getter")
    void testLoginRequest() {
        final LoginRequest request = new LoginRequest("13800138000", "password123", true);

        assertEquals("13800138000", request.getPhone());
        assertEquals("password123", request.getPassword());
        assertTrue(request.getRememberMe());
    }

    @Test
    @DisplayName("LoginResponse 构造和 Getter")
    void testLoginResponse() {
        final UserInfo user = new UserInfo(1L, "张三", "http://avatar.jpg", "程序员");
        final LoginResponse response = new LoginResponse("access-token", "refresh-token", 7200L, user);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(7200L, response.getExpiresIn());
        assertEquals(1L, response.getUser().getUserId());
    }

    @Test
    @DisplayName("UserInfo 构造和 Getter")
    void testUserInfo() {
        final UserInfo user = new UserInfo(1L, "张三", "http://avatar.jpg", "程序员");

        assertEquals(1L, user.getUserId());
        assertEquals("张三", user.getUsername());
        assertEquals("http://avatar.jpg", user.getAvatar());
        assertEquals("程序员", user.getBio());
    }

    @Test
    @DisplayName("ApiResponse 成功判断")
    void testApiResponseSuccess() {
        final ApiResponse<Object> response = new ApiResponse<>();
        response.setCode(200);

        assertTrue(response.isSuccess());
    }

    @Test
    @DisplayName("ApiResponse 失败判断")
    void testApiResponseFailure() {
        final ApiResponse<Object> response = new ApiResponse<>();
        response.setCode(1004);

        assertFalse(response.isSuccess());
    }
}