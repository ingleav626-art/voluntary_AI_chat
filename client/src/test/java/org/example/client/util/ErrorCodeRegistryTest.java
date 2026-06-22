package org.example.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ErrorCodeRegistry 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ErrorCodeRegistry 统一错误码映射测试")
class ErrorCodeRegistryTest {

    @Test
    @DisplayName("认证模块错误码映射")
    void testAuthErrorCodes() {
        assertEquals("手机号已注册", ErrorCodeRegistry.getMessage(1001));
        assertEquals("验证码错误或已过期", ErrorCodeRegistry.getMessage(1002));
        assertEquals("用户名已存在", ErrorCodeRegistry.getMessage(1003));
        assertEquals("账号或密码错误", ErrorCodeRegistry.getMessage(1004));
        assertEquals("登录次数过多，账号已锁定", ErrorCodeRegistry.getMessage(1005));
        assertEquals("登录已失效，请重新登录", ErrorCodeRegistry.getMessage(1006));
    }

    @Test
    @DisplayName("好友模块错误码映射")
    void testFriendErrorCodes() {
        assertEquals("好友申请已存在，请等待对方处理", ErrorCodeRegistry.getMessage(2001));
        assertEquals("对方已是你的好友", ErrorCodeRegistry.getMessage(2002));
        assertEquals("不能添加自己为好友", ErrorCodeRegistry.getMessage(2003));
    }

    @Test
    @DisplayName("群组模块错误码映射")
    void testGroupErrorCodes() {
        assertEquals("无权执行此操作", ErrorCodeRegistry.getMessage(3001));
        assertEquals("群成员已满", ErrorCodeRegistry.getMessage(3002));
        assertEquals("你已在群中", ErrorCodeRegistry.getMessage(3003));
    }

    @Test
    @DisplayName("消息模块错误码映射")
    void testMessageErrorCodes() {
        assertEquals("消息已超过2分钟，不可撤回", ErrorCodeRegistry.getMessage(4001));
        assertEquals("无权撤回他人消息", ErrorCodeRegistry.getMessage(4002));
        assertEquals("图片格式不支持", ErrorCodeRegistry.getMessage(4003));
        assertEquals("图片大小超出限制", ErrorCodeRegistry.getMessage(4004));
    }

    @Test
    @DisplayName("AI模块错误码映射")
    void testAiErrorCodes() {
        assertEquals("AI服务配置无效", ErrorCodeRegistry.getMessage(5001));
        assertEquals("AI不存在或已禁用", ErrorCodeRegistry.getMessage(5002));
    }

    @Test
    @DisplayName("HTTP通用错误码映射")
    void testHttpErrorCodes() {
        assertEquals("请求参数错误，请检查输入", ErrorCodeRegistry.getMessage(400));
        assertEquals("登录已失效，请重新登录", ErrorCodeRegistry.getMessage(401));
        assertEquals("无权访问，请先登录", ErrorCodeRegistry.getMessage(403));
        assertEquals("请求的资源不存在", ErrorCodeRegistry.getMessage(404));
        assertEquals("服务器异常，请稍后重试", ErrorCodeRegistry.getMessage(500));
    }

    @Test
    @DisplayName("未注册错误码返回后端原始消息")
    void testUnregisteredCodeWithBackendMessage() {
        final String backendMsg = "自定义后端错误消息";
        assertEquals(backendMsg, ErrorCodeRegistry.getMessage(9999, backendMsg));
    }

    @Test
    @DisplayName("未注册错误码且无后端消息返回默认提示")
    void testUnregisteredCodeWithoutBackendMessage() {
        assertEquals("操作失败，请稍后重试", ErrorCodeRegistry.getMessage(9999));
        assertEquals("操作失败，请稍后重试", ErrorCodeRegistry.getMessage(9999, null));
        assertEquals("操作失败，请稍后重试", ErrorCodeRegistry.getMessage(9999, ""));
    }

    @Test
    @DisplayName("已注册错误码优先使用映射消息")
    void testRegisteredCodeOverridesBackendMessage() {
        // 即使传了后端消息，已注册的错误码也应返回映射消息
        assertEquals("手机号已注册", ErrorCodeRegistry.getMessage(1001, "后端原始消息"));
        assertEquals("账号或密码错误", ErrorCodeRegistry.getMessage(1004, "backend msg"));
    }

    @Test
    @DisplayName("isRegistered 判断错误码是否已注册")
    void testIsRegistered() {
        assertTrue(ErrorCodeRegistry.isRegistered(1001));
        assertTrue(ErrorCodeRegistry.isRegistered(2003));
        assertTrue(ErrorCodeRegistry.isRegistered(4001));
        assertFalse(ErrorCodeRegistry.isRegistered(9999));
        assertFalse(ErrorCodeRegistry.isRegistered(0));
    }

    @Test
    @DisplayName("动态注册新错误码")
    void testDynamicRegister() {
        final int newCode = 9001;
        assertFalse(ErrorCodeRegistry.isRegistered(newCode));

        ErrorCodeRegistry.register(newCode, "新注册的错误提示");
        assertTrue(ErrorCodeRegistry.isRegistered(newCode));
        assertEquals("新注册的错误提示", ErrorCodeRegistry.getMessage(newCode));
    }

    @Test
    @DisplayName("getMessage 带后端消息的重载方法")
    void testGetMessageWithBackendMessage() {
        assertNotNull(ErrorCodeRegistry.getMessage(1001, null));
        assertNotNull(ErrorCodeRegistry.getMessage(1001, ""));
        assertNotNull(ErrorCodeRegistry.getMessage(1001, "任意消息"));
    }
}
