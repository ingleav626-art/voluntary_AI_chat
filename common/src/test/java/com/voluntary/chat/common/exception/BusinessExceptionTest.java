package com.voluntary.chat.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BusinessException 测试")
class BusinessExceptionTest {

  @Test
  @DisplayName("通过 ErrorCode 构造")
  void constructorWithErrorCode() {
    BusinessException ex = new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
    assertEquals(ErrorCode.PHONE_ALREADY_REGISTERED, ex.getErrorCode());
    assertEquals("手机号已注册", ex.getMessage());
  }

  @Test
  @DisplayName("通过 ErrorCode + 自定义消息构造")
  void constructorWithErrorCodeAndDetail() {
    BusinessException ex = new BusinessException(ErrorCode.ACCOUNT_LOCKED, "自定义详情");
    assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
    assertEquals("自定义详情", ex.getMessage());
  }

  @Test
  @DisplayName("验证 BusinessException 是 RuntimeException")
  void shouldBeRuntimeException() {
    BusinessException ex = new BusinessException(ErrorCode.INTERNAL_ERROR);
    assertInstanceOf(RuntimeException.class, ex);
  }
}
