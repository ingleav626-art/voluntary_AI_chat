package org.example.client.view;

import javafx.beans.property.StringProperty;
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
        final boolean[] successCalled = {false};
        final boolean[] backCalled = {false};

        viewModel.setOnRegisterSuccess(response -> successCalled[0] = true);
        viewModel.setOnBackToLogin(() -> backCalled[0] = true);

        viewModel.backToLogin();
        assertTrue(backCalled[0]);
        assertFalse(successCalled[0]);
    }
}
