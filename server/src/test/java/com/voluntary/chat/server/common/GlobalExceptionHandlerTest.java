package com.voluntary.chat.server.common;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("处理 BusinessException")
    void handleBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        ApiResult<Void> result = handler.handleBusinessException(ex);

        assertEquals(1001, result.getCode());
        assertEquals("手机号已注册", result.getMessage());
    }

    @Test
    @DisplayName("处理 MethodArgumentNotValidException")
    void handleValidationException() throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "phone", "手机号不能为空"));
        bindingResult.addError(new FieldError("target", "username", "用户名不能为空"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ApiResult<Void> result = handler.handleValidationException(ex);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("手机号不能为空"));
        assertTrue(result.getMessage().contains("用户名不能为空"));
    }

    @Test
    @DisplayName("处理 HttpMessageNotReadableException")
    void handleHttpMessageNotReadableException() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("test msg");

        ApiResult<Void> result = handler.handleHttpMessageNotReadableException(ex);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("请求体格式错误"));
    }

    @Test
    @DisplayName("处理 IllegalArgumentException")
    void handleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("参数不合法");

        ApiResult<Void> result = handler.handleIllegalArgumentException(ex);

        assertEquals(400, result.getCode());
        assertEquals("参数不合法", result.getMessage());
    }

    @Test
    @DisplayName("处理通用 Exception")
    void handleException() {
        Exception ex = new RuntimeException("内部错误");

        ApiResult<Void> result = handler.handleException(ex);

        assertEquals(500, result.getCode());
        assertEquals("内部错误", result.getMessage());
    }
}
