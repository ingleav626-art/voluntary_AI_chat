package com.voluntary.chat.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorCode 枚举测试")
class ErrorCodeTest {

    @Test
    @DisplayName("成功状态码")
    void success() {
        assertEquals(200, ErrorCode.SUCCESS.getCode());
        assertEquals("成功", ErrorCode.SUCCESS.getMessage());
    }

    @Test
    @DisplayName("请求参数错误")
    void badRequest() {
        assertEquals(400, ErrorCode.BAD_REQUEST.getCode());
        assertEquals("请求参数错误", ErrorCode.BAD_REQUEST.getMessage());
    }

    @Test
    @DisplayName("未认证")
    void unauthorized() {
        assertEquals(401, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals("未认证", ErrorCode.UNAUTHORIZED.getMessage());
    }

    @Test
    @DisplayName("手机号已注册")
    void phoneAlreadyRegistered() {
        assertEquals(1001, ErrorCode.PHONE_ALREADY_REGISTERED.getCode());
        assertEquals("手机号已注册", ErrorCode.PHONE_ALREADY_REGISTERED.getMessage());
    }

    @Test
    @DisplayName("验证码错误或已过期")
    void verificationCodeError() {
        assertEquals(1002, ErrorCode.VERIFICATION_CODE_ERROR.getCode());
        assertEquals("验证码错误或已过期", ErrorCode.VERIFICATION_CODE_ERROR.getMessage());
    }

    @Test
    @DisplayName("用户名已存在")
    void usernameAlreadyExists() {
        assertEquals(1003, ErrorCode.USERNAME_ALREADY_EXISTS.getCode());
        assertEquals("用户名已存在", ErrorCode.USERNAME_ALREADY_EXISTS.getMessage());
    }

    @Test
    @DisplayName("账号或密码错误")
    void accountOrPasswordError() {
        assertEquals(1004, ErrorCode.ACCOUNT_OR_PASSWORD_ERROR.getCode());
        assertEquals("账号或密码错误", ErrorCode.ACCOUNT_OR_PASSWORD_ERROR.getMessage());
    }

    @Test
    @DisplayName("账号锁定")
    void accountLocked() {
        assertEquals(1005, ErrorCode.ACCOUNT_LOCKED.getCode());
        assertEquals("登录次数过多，账号已锁定", ErrorCode.ACCOUNT_LOCKED.getMessage());
    }

    @Test
    @DisplayName("Refresh Token 失效")
    void refreshTokenInvalid() {
        assertEquals(1006, ErrorCode.REFRESH_TOKEN_INVALID.getCode());
        assertEquals("Refresh Token 已失效", ErrorCode.REFRESH_TOKEN_INVALID.getMessage());
    }

    @Test
    @DisplayName("好友相关错误码")
    void friendErrorCodes() {
        assertEquals(2001, ErrorCode.FRIEND_APPLY_EXISTS.getCode());
        assertEquals(2002, ErrorCode.ALREADY_FRIENDS.getCode());
        assertEquals(2003, ErrorCode.CANNOT_ADD_SELF.getCode());
    }

    @Test
    @DisplayName("群组相关错误码")
    void groupErrorCodes() {
        assertEquals(3001, ErrorCode.NO_PERMISSION.getCode());
        assertEquals(3002, ErrorCode.GROUP_MEMBER_FULL.getCode());
        assertEquals(3003, ErrorCode.ALREADY_IN_GROUP.getCode());
    }

    @Test
    @DisplayName("消息相关错误码")
    void messageErrorCodes() {
        assertEquals(4001, ErrorCode.MESSAGE_RECALL_TIMEOUT.getCode());
        assertEquals(4002, ErrorCode.NO_PERMISSION_TO_RECALL.getCode());
        assertEquals(4003, ErrorCode.IMAGE_FORMAT_NOT_SUPPORTED.getCode());
        assertEquals(4004, ErrorCode.IMAGE_SIZE_EXCEEDED.getCode());
    }

    @Test
    @DisplayName("AI 相关错误码")
    void aiErrorCodes() {
        assertEquals(5001, ErrorCode.AI_API_KEY_INVALID.getCode());
        assertEquals(5002, ErrorCode.AI_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("所有错误码应唯一")
    void allCodesShouldBeUnique() {
        long distinctCount = java.util.Arrays.stream(ErrorCode.values())
                .mapToInt(ErrorCode::getCode)
                .distinct()
                .count();
        assertEquals(ErrorCode.values().length, distinctCount);
    }

    @Test
    @DisplayName("所有错误码消息不应为空")
    void allMessagesShouldNotBeBlank() {
        java.util.Arrays.stream(ErrorCode.values())
                .forEach(e -> assertFalse(e.getMessage().isBlank(), e.name() + " 的消息为空"));
    }
}
