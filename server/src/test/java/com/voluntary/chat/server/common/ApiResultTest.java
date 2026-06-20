package com.voluntary.chat.server.common;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiResult 单元测试")
class ApiResultTest {

    @Test
    @DisplayName("ok() 无参数")
    void okNoArgs() {
        ApiResult<Void> result = ApiResult.ok();
        assertEquals(200, result.getCode());
        assertEquals("成功", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("ok(T data)")
    void okWithData() {
        ApiResult<String> result = ApiResult.ok("hello");
        assertEquals(200, result.getCode());
        assertEquals("成功", result.getMessage());
        assertEquals("hello", result.getData());
    }

    @Test
    @DisplayName("ok(String message, T data)")
    void okWithMessageAndData() {
        ApiResult<String> result = ApiResult.ok("自定义消息", "world");
        assertEquals(200, result.getCode());
        assertEquals("自定义消息", result.getMessage());
        assertEquals("world", result.getData());
    }

    @Test
    @DisplayName("error(ErrorCode)")
    void errorByErrorCode() {
        ApiResult<Void> result = ApiResult.error(ErrorCode.PHONE_ALREADY_REGISTERED);
        assertEquals(1001, result.getCode());
        assertEquals("手机号已注册", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("error(int code, String message)")
    void errorByCodeAndMessage() {
        ApiResult<Void> result = ApiResult.error(400, "参数错误");
        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("error(BusinessException)")
    void errorByBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        ApiResult<Void> result = ApiResult.error(ex);
        assertEquals(1005, result.getCode());
        assertEquals("登录次数过多，账号已锁定", result.getMessage());
    }

    @Test
    @DisplayName("Setter和Getter")
    void setterAndGetter() {
        ApiResult<String> result = new ApiResult<>();
        result.setCode(200);
        result.setMessage("ok");
        result.setData("test");

        assertEquals(200, result.getCode());
        assertEquals("ok", result.getMessage());
        assertEquals("test", result.getData());
    }
}
