package org.example.client.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ForgotPasswordViewModel 单元测试
 *
 * 测试验证逻辑，不测试网络请求（需要 Mock 服务端）
 */
@DisplayName("ForgotPasswordViewModel 测试")
class ForgotPasswordViewModelTest {

    private ForgotPasswordViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new ForgotPasswordViewModel();
    }

    @Test
    @DisplayName("属性初始化")
    void testPropertiesInitialized() {
        assertEquals("", viewModel.phoneProperty().get());
        assertEquals("", viewModel.codeProperty().get());
        assertEquals("", viewModel.newPasswordProperty().get());
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
    @DisplayName("重置密码验证 - 手机号格式错误")
    void testResetPasswordInvalidPhone() {
        viewModel.phoneProperty().set("12345");
        viewModel.codeProperty().set("123456");
        viewModel.newPasswordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.resetPassword();
        assertEquals("手机号格式错误", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("重置密码验证 - 验证码长度错误")
    void testResetPasswordInvalidCode() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("12345");
        viewModel.newPasswordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.resetPassword();
        assertEquals("验证码为6位数字", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("重置密码验证 - 密码太短")
    void testResetPasswordTooShort() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.newPasswordProperty().set("12345");
        viewModel.confirmPasswordProperty().set("12345");

        viewModel.resetPassword();
        assertEquals("密码长度6-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("重置密码验证 - 两次密码不一致")
    void testResetPasswordMismatch() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.newPasswordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password456");

        viewModel.resetPassword();
        assertEquals("两次密码不一致", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("重置密码验证 - 全部有效不报错")
    void testResetPasswordValidInput() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.codeProperty().set("123456");
        viewModel.newPasswordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");

        viewModel.resetPassword();
        // 验证通过后进入网络请求，loading应为true（异步请求已发出）
        // 但由于没有真实服务端，loading可能在测试线程中仍为true
        // 关键是没有验证错误
        assertNotEquals("手机号格式错误", viewModel.errorMessageProperty().get());
        assertNotEquals("验证码为6位数字", viewModel.errorMessageProperty().get());
        assertNotEquals("密码长度6-50个字符", viewModel.errorMessageProperty().get());
        assertNotEquals("两次密码不一致", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("回调设置")
    void testCallbacks() {
        final boolean[] successCalled = {false};
        final boolean[] backCalled = {false};

        viewModel.setOnResetSuccess(phone -> successCalled[0] = true);
        viewModel.setOnBackToLogin(() -> backCalled[0] = true);

        // backToLogin 没有在 ForgotPasswordViewModel 中定义
        // 回调通过 Controller 直接调用 App.switchToLogin
        assertFalse(successCalled[0]);
        assertFalse(backCalled[0]);
    }

    @Test
    @DisplayName("发送验证码 - 有效手机号不报验证错误")
    void testSendSmsCodeValidPhone() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.sendSmsCode();
        // 不会设置验证错误（网络请求可能失败，但不是验证错误）
        assertNotEquals("请输入手机号", viewModel.errorMessageProperty().get());
        assertNotEquals("手机号格式错误", viewModel.errorMessageProperty().get());
    }
}
