package org.example.client.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注册相关模型测试
 */
@DisplayName("注册模型测试")
class RegisterModelTest {

    @Test
    @DisplayName("RegisterRequest 构造和 Getter")
    void testRegisterRequest() {
        final RegisterRequest request = new RegisterRequest(
                "13800138000", "123456", "张三", "password123");

        assertEquals("13800138000", request.getPhone());
        assertEquals("123456", request.getCode());
        assertEquals("张三", request.getUsername());
        assertEquals("password123", request.getPassword());
    }

    @Test
    @DisplayName("RegisterRequest Setter")
    void testRegisterRequestSetter() {
        final RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("654321");
        request.setUsername("李四");
        request.setPassword("mypass");

        assertEquals("13800138000", request.getPhone());
        assertEquals("654321", request.getCode());
        assertEquals("李四", request.getUsername());
        assertEquals("mypass", request.getPassword());
    }

    @Test
    @DisplayName("SmsSendRequest 构造和 Getter")
    void testSmsSendRequest() {
        final SmsSendRequest request = new SmsSendRequest("13800138000");

        assertEquals("13800138000", request.getPhone());
    }

    @Test
    @DisplayName("RegisterResponse 构造和 Getter")
    void testRegisterResponse() {
        final UserInfo user = new UserInfo(1L, "张三", "http://example.com/avatar.jpg", "个人简介");
        final RegisterResponse response = new RegisterResponse(
                "access-token", "refresh-token", 7200L, user);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(7200L, response.getExpiresIn());
        assertEquals(1L, response.getUser().getUserId());
        assertEquals("张三", response.getUser().getUsername());
    }

    @Test
    @DisplayName("RegisterResponse Setter")
    void testRegisterResponseSetter() {
        final RegisterResponse response = new RegisterResponse();
        response.setAccessToken("new-access");
        response.setRefreshToken("new-refresh");
        response.setExpiresIn(3600L);
        final UserInfo user = new UserInfo(2L, "李四", null, null);
        response.setUser(user);

        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(2L, response.getUser().getUserId());
        assertEquals("李四", response.getUser().getUsername());
    }
}
