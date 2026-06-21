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
        final UserInfo userInfo = new UserInfo(1L, null, "张三", null, null,
                null, null, null, null, null);
        final RegisterResponse response = new RegisterResponse(
                "token123", "refresh456", 7200L, userInfo);

        assertEquals("token123", response.getAccessToken());
        assertEquals("refresh456", response.getRefreshToken());
        assertEquals(7200L, response.getExpiresIn());
        assertNotNull(response.getUser());
        assertEquals(1L, response.getUser().getUserId());
        assertEquals("张三", response.getUser().getUsername());
    }

    @Test
    @DisplayName("RegisterResponse Setter")
    void testRegisterResponseSetter() {
        final RegisterResponse response = new RegisterResponse();
        response.setAccessToken("token789");
        response.setRefreshToken("refresh012");
        response.setExpiresIn(3600L);
        response.setUser(new UserInfo(2L, null, "李四", null, null,
                null, null, null, null, null));

        assertEquals("token789", response.getAccessToken());
        assertEquals("refresh012", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
        assertNotNull(response.getUser());
        assertEquals(2L, response.getUser().getUserId());
        assertEquals("李四", response.getUser().getUsername());
    }
}
