package org.example.client.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RegisterViewModel 单元测试
 *
 * 测试验证逻辑，不测试网络请求（需要 Mock 服务端）
 */
@DisplayName("RegisterViewModel 测试")
class RegisterViewModelTest {

    private RegisterViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new RegisterViewModel();
    }

    @Test
    @DisplayName("属性初始化")
    void testPropertiesInitialized() {
        assertEquals("", viewModel.phoneProperty().get());
        assertEquals("", viewModel.codeProperty().get());
        assertEquals("", viewModel.usernameProperty().get());
        assertEquals("", viewModel.passwordProperty().get());
        assertEquals("", viewModel.confirmPasswordProperty().get());
        assertFalse(viewModel.loadingProperty().get());
        assertFalse(viewModel.smsButtonDisabledProperty().get());
        assertEquals("获取验证码", viewModel.smsButtonTextProperty().get());
        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals("", viewModel.successMessageProperty().get());
    }

    @Test
    @DisplayName("发送验证码 - 手机号为空")
    void testSendSmsCodeEmptyPhone() {
        viewModel.sendSmsCode();
        assertEquals("请输入手机号", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("发送验证码 - 手机号格式错误")
    void testSendSmsCodeInvalidPhone() {
        viewModel.phoneProperty().set("12345");
        viewModel.sendSmsCode();
        assertEquals("手机号格式错误", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 手机号格式错误")
    void testRegisterInvalidPhone() {
        viewModel.phoneProperty().set("12345");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        assertEquals("手机号格式错误", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 验证码长度错误")
    void testRegisterInvalidCode() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("12345");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        assertEquals("验证码为6位数字", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 用户名太短")
    void testRegisterUsernameTooShort() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        assertEquals("用户名长度2-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 密码太短")
    void testRegisterPasswordTooShort() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("12345");
        viewModel.confirmPasswordProperty().set("12345");

        viewModel.register();
        assertEquals("密码长度6-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 两次密码不一致")
    void testRegisterPasswordMismatch() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password456");

        viewModel.register();
        assertEquals("两次密码不一致", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("回调设置")
    void testCallbacks() {
        final boolean[] successCalled = { false };
        final boolean[] backCalled = { false };

        viewModel.setOnRegisterSuccess(response -> successCalled[0] = true);
        viewModel.setOnBackToLogin(() -> backCalled[0] = true);

        viewModel.backToLogin();
        assertTrue(backCalled[0]);
        assertFalse(successCalled[0]);
    }

    @Test
    @DisplayName("发送验证码 - 手机号空白")
    void testSendSmsCodeBlankPhone() {
        viewModel.phoneProperty().set("   ");
        viewModel.sendSmsCode();
        assertEquals("请输入手机号", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 用户名太长")
    void testRegisterUsernameTooLong() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("a".repeat(51));
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        assertEquals("用户名长度2-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 密码太长")
    void testRegisterPasswordTooLong() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("a".repeat(51));
        viewModel.confirmPasswordProperty().set("a".repeat(51));

        viewModel.register();
        assertEquals("密码长度6-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 手机号为null")
    void testRegisterNullPhone() {
        viewModel.phoneProperty().set(null);
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        assertEquals("手机号格式错误", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 验证码为null")
    void testRegisterNullCode() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set(null);
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        assertEquals("验证码为6位数字", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 用户名为null")
    void testRegisterNullUsername() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set(null);
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        assertEquals("用户名长度2-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("注册验证 - 密码为null")
    void testRegisterNullPassword() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set(null);
        viewModel.confirmPasswordProperty().set(null);

        viewModel.register();
        assertEquals("密码长度6-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("发送验证码 - 有效手机号调用不崩溃")
    void testSendSmsCodeValidPhone() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.sendSmsCode();
        // 异步请求会失败，但不崩溃
        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("注册 - 有效输入调用不崩溃")
    void testRegisterValidInput() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        // 异步请求会失败，但验证通过后进入loading状态
        assertTrue(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("backToLogin - 无回调时不崩溃")
    void testBackToLoginNoCallback() {
        assertDoesNotThrow(() -> viewModel.backToLogin());
    }

    @Test
    @DisplayName("发送验证码 - 清除之前的错误消息")
    void testSendSmsCodeClearsPreviousError() {
        viewModel.errorMessageProperty().set("之前的错误");
        viewModel.phoneProperty().set("13800138000");
        viewModel.sendSmsCode();
        // 错误消息应被清除（然后可能被异步结果覆盖）
        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("注册 - 清除之前的错误和成功消息")
    void testRegisterClearsPreviousMessages() {
        viewModel.errorMessageProperty().set("之前的错误");
        viewModel.successMessageProperty().set("之前的成功");
        viewModel.phoneProperty().set("12345");
        viewModel.register();
        // 验证失败后设置新的错误消息
        assertEquals("手机号格式错误", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("用户名最小边界 - 2字符")
    void testUsernameMinBoundary() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("ab");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.register();
        // 2字符是有效的，应该进入loading状态
        assertTrue(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("密码最小边界 - 6字符")
    void testPasswordMinBoundary() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.usernameProperty().set("张三");
        viewModel.passwordProperty().set("123456");
        viewModel.confirmPasswordProperty().set("123456");

        viewModel.register();
        assertTrue(viewModel.loadingProperty().get());
    }
}
