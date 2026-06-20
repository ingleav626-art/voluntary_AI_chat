package org.example.client.util;

import org.example.client.model.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonUtils 单元测试
 */
@DisplayName("JsonUtils 测试")
class JsonUtilsTest {

    @Test
    @DisplayName("对象转 JSON 字符串")
    void testToJson() {
        final LoginRequest request = new LoginRequest("13800138000", "password123", true);
        final String json = JsonUtils.toJson(request);

        assertNotNull(json);
        assertTrue(json.contains("13800138000"));
        assertTrue(json.contains("password123"));
        assertTrue(json.contains("true"));
    }

    @Test
    @DisplayName("JSON 字符串转对象")
    void testFromJson() {
        final String json = "{\"phone\":\"13800138000\",\"password\":\"password123\",\"rememberMe\":true}";
        final LoginRequest request = JsonUtils.fromJson(json, LoginRequest.class);

        assertNotNull(request);
        assertEquals("13800138000", request.getPhone());
        assertEquals("password123", request.getPassword());
        assertTrue(request.getRememberMe());
    }

    @Test
    @DisplayName("序列化 null 对象")
    void testToJsonNull() {
        final String json = JsonUtils.toJson(null);
        assertEquals("null", json);
    }

    @Test
    @DisplayName("反序列化无效 JSON")
    void testFromJsonInvalid() {
        final String invalidJson = "invalid json string";
        final LoginRequest request = JsonUtils.fromJson(invalidJson, LoginRequest.class);
        assertNull(request);
    }

    @Test
    @DisplayName("反序列化 null JSON")
    void testFromJsonNull() {
        final LoginRequest request = JsonUtils.fromJson(null, LoginRequest.class);
        assertNull(request);
    }

    @Test
    @DisplayName("获取 ObjectMapper 实例")
    void testGetMapper() {
        assertNotNull(JsonUtils.getMapper());
    }

    @Test
    @DisplayName("泛型类型反序列化")
    void testFromJsonWithType() {
        final String json = "{\"code\":200,\"message\":\"success\",\"data\":null}";
        final var javaType = JsonUtils.getMapper().getTypeFactory()
                .constructParametricType(
                        org.example.client.model.ApiResponse.class,
                        org.example.client.model.LoginResponse.class);

        final org.example.client.model.ApiResponse<org.example.client.model.LoginResponse> response =
                JsonUtils.fromJson(json, javaType);
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
    }
}