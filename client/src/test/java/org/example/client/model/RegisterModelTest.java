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
        final RegisterResponse response = new RegisterResponse(1L, "张三");

        assertEquals(1L, response.getUserId());
        assertEquals("张三", response.getUsername());
    }

    @Test
    @DisplayName("RegisterResponse Setter")
    void testRegisterResponseSetter() {
        final RegisterResponse response = new RegisterResponse();
        response.setUserId(2L);
        response.setUsername("李四");

        assertEquals(2L, response.getUserId());
        assertEquals("李四", response.getUsername());
    }
}
