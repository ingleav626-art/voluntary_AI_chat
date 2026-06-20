package org.example.client.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginViewModel 单元测试
 */
@DisplayName("LoginViewModel 测试")
class LoginViewModelTest {

    private LoginViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new LoginViewModel();
    }

    @Test
    @DisplayName("初始状态")
    void testInitialState() {
        assertEquals("", viewModel.phoneProperty().get());
        assertEquals("", viewModel.passwordProperty().get());
        assertFalse(viewModel.rememberMeProperty().get());
        assertFalse(viewModel.loadingProperty().get());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("空手机号验证")
    void testEmptyPhone() {
        viewModel.phoneProperty().set("");
        viewModel.passwordProperty().set("password123");
        viewModel.login();

        assertEquals("请输入手机号", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("手机号格式错误")
    void testInvalidPhoneFormat() {
        viewModel.phoneProperty().set("12345678901");
        viewModel.passwordProperty().set("password123");
        viewModel.login();

        assertEquals("手机号格式错误", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("空密码验证")
    void testEmptyPassword() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.passwordProperty().set("");
        viewModel.login();

        assertEquals("请输入密码", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("密码长度不足")
    void testShortPassword() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.passwordProperty().set("12345");
        viewModel.login();

        assertEquals("密码长度至少6位", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("有效输入验证")
    void testValidInput() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.passwordProperty().set("password123");

        // 由于网络请求会失败，这里只验证输入校验通过
        viewModel.login();

        // 输入校验通过后，errorMessage 应为空或网络错误
        assertNotEquals("请输入手机号", viewModel.errorMessageProperty().get());
        assertNotEquals("手机号格式错误", viewModel.errorMessageProperty().get());
        assertNotEquals("请输入密码", viewModel.errorMessageProperty().get());
        assertNotEquals("密码长度至少6位", viewModel.errorMessageProperty().get());
    }
}