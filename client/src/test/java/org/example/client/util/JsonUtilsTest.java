package org.example.client.util;

import com.fasterxml.jackson.databind.JavaType;
import org.example.client.model.ApiResponse;
import org.example.client.model.LoginRequest;
import org.example.client.model.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonUtils 测试")
class JsonUtilsTest {

    @Test
    @DisplayName("对象转 JSON 字符串")
    void testToJson() {
        LoginRequest request = new LoginRequest("13800138000", "password123", true);
        String json = JsonUtils.toJson(request);
        assertNotNull(json);
        assertTrue(json.contains("13800138000"));
        assertTrue(json.contains("password123"));
        assertTrue(json.contains("true"));
    }

    @Test
    @DisplayName("序列化 null 对象")
    void testToJsonNull() {
        assertEquals("null", JsonUtils.toJson(null));
    }

    @Test
    @DisplayName("JSON 字符串转对象")
    void testFromJson() {
        String json = "{\"phone\":\"13800138000\",\"password\":\"password123\",\"rememberMe\":true}";
        LoginRequest request = JsonUtils.fromJson(json, LoginRequest.class);
        assertNotNull(request);
        assertEquals("13800138000", request.getPhone());
        assertEquals("password123", request.getPassword());
        assertTrue(request.getRememberMe());
    }

    @Test
    @DisplayName("反序列化 null 返回 null")
    void testFromJsonNull() {
        assertNull(JsonUtils.fromJson(null, LoginRequest.class));
    }

    @Test
    @DisplayName("反序列化空字符串返回 null")
    void testFromJsonEmpty() {
        assertNull(JsonUtils.fromJson("", LoginRequest.class));
        assertNull(JsonUtils.fromJson("   ", LoginRequest.class));
    }

    @Test
    @DisplayName("反序列化无效 JSON 返回 null")
    void testFromJsonInvalid() {
        assertNull(JsonUtils.fromJson("invalid json string", LoginRequest.class));
    }

    @Test
    @DisplayName("反序列化 JSON 数组不匹配返回 null")
    void testFromJsonArrayMismatch() {
        assertNull(JsonUtils.fromJson("[1,2,3]", LoginRequest.class));
    }

    @Test
    @DisplayName("获取 ObjectMapper 实例")
    void testGetMapper() {
        assertNotNull(JsonUtils.getMapper());
    }

    @Test
    @DisplayName("泛型类型反序列化")
    void testFromJsonWithType() {
        String json = "{\"code\":200,\"message\":\"success\",\"data\":null}";
        JavaType javaType = JsonUtils.getMapper().getTypeFactory()
                .constructParametricType(ApiResponse.class, LoginResponse.class);
        ApiResponse<LoginResponse> response = JsonUtils.fromJson(json, javaType);
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
    }

    @Test
    @DisplayName("泛型类型反序列化 - null 输入")
    void testFromJsonWithTypeNull() {
        JavaType javaType = JsonUtils.getMapper().getTypeFactory()
                .constructParametricType(ApiResponse.class, LoginResponse.class);
        assertNull(JsonUtils.fromJson(null, javaType));
    }

    @Test
    @DisplayName("泛型类型反序列化 - 空字符串")
    void testFromJsonWithTypeEmpty() {
        JavaType javaType = JsonUtils.getMapper().getTypeFactory()
                .constructParametricType(ApiResponse.class, LoginResponse.class);
        assertNull(JsonUtils.fromJson("", javaType));
    }

    @Test
    @DisplayName("泛型类型反序列化 - 无效 JSON")
    void testFromJsonWithTypeInvalid() {
        JavaType javaType = JsonUtils.getMapper().getTypeFactory()
                .constructParametricType(ApiResponse.class, LoginResponse.class);
        assertNull(JsonUtils.fromJson("not json", javaType));
    }

    @Test
    @DisplayName("序列化循环引用对象不会抛异常")
    void testToJsonCircularRef() {
        // 循环引用场景：对象包含自身引用
        CircularObj obj = new CircularObj();
        obj.self = obj;
        String json = JsonUtils.toJson(obj);
        // Jackson 默认对循环引用会抛异常，返回 null 或抛出
        // 只要不抛未捕获异常即可
        assertNull(json);
    }

    static class CircularObj {
        public CircularObj self;
    }
}
