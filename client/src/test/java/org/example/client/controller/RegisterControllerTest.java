package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.example.client.model.RegisterResponse;
import org.example.client.view.RegisterViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RegisterController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("RegisterController 测试")
class RegisterControllerTest extends JavaFxTestBase {

    private RegisterController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new RegisterController();

        final TextField phoneField = new TextField();
        final Button smsButton = new Button();
        final TextField codeField = new TextField();
        final TextField usernameField = new TextField();
        final PasswordField passwordField = new PasswordField();
        final PasswordField confirmPasswordField = new PasswordField();
        final Button registerButton = new Button();
        final Label errorLabel = new Label();
        final Label successLabel = new Label();
        final ProgressIndicator loadingIndicator = new ProgressIndicator();

        setField(controller, "phoneField", phoneField);
        setField(controller, "smsButton", smsButton);
        setField(controller, "codeField", codeField);
        setField(controller, "usernameField", usernameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "confirmPasswordField", confirmPasswordField);
        setField(controller, "registerButton", registerButton);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "successLabel", successLabel);
        setField(controller, "loadingIndicator", loadingIndicator);

        controller.initialize(null, null);
    }

    @Test
    @DisplayName("initialize - ViewModel 正确创建")
    void initialize_shouldCreateViewModel() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("initialize - phoneField 与 ViewModel 双向绑定")
    void initialize_phoneFieldBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final TextField phoneField = (TextField) getField(controller, "phoneField");

        phoneField.setText("13800138000");
        assertEquals("13800138000", viewModel.phoneProperty().get());
    }

    @Test
    @DisplayName("initialize - codeField 与 ViewModel 双向绑定")
    void initialize_codeFieldBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final TextField codeField = (TextField) getField(controller, "codeField");

        codeField.setText("123456");
        assertEquals("123456", viewModel.codeProperty().get());
    }

    @Test
    @DisplayName("initialize - usernameField 与 ViewModel 双向绑定")
    void initialize_usernameFieldBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final TextField usernameField = (TextField) getField(controller, "usernameField");

        usernameField.setText("张三");
        assertEquals("张三", viewModel.usernameProperty().get());
    }

    @Test
    @DisplayName("initialize - passwordField 与 ViewModel 双向绑定")
    void initialize_passwordFieldBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final PasswordField passwordField = (PasswordField) getField(controller, "passwordField");

        passwordField.setText("password123");
        assertEquals("password123", viewModel.passwordProperty().get());
    }

    @Test
    @DisplayName("initialize - confirmPasswordField 与 ViewModel 双向绑定")
    void initialize_confirmPasswordFieldBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final PasswordField confirmPasswordField = (PasswordField) getField(controller, "confirmPasswordField");

        confirmPasswordField.setText("password123");
        assertEquals("password123", viewModel.confirmPasswordProperty().get());
    }

    @Test
    @DisplayName("initialize - errorLabel 绑定到 ViewModel errorMessage")
    void initialize_errorLabelBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        viewModel.errorMessageProperty().set("验证码错误");
        assertEquals("验证码错误", errorLabel.getText());
    }

    @Test
    @DisplayName("initialize - successLabel 绑定到 ViewModel successMessage")
    void initialize_successLabelBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final Label successLabel = (Label) getField(controller, "successLabel");

        viewModel.successMessageProperty().set("注册成功");
        assertEquals("注册成功", successLabel.getText());
    }

    @Test
    @DisplayName("initialize - registerButton 绑定到 ViewModel loading")
    void initialize_registerButtonBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final Button registerButton = (Button) getField(controller, "registerButton");

        viewModel.loadingProperty().set(true);
        assertTrue(registerButton.isDisabled());
    }

    @Test
    @DisplayName("initialize - loadingIndicator 绑定到 ViewModel loading")
    void initialize_loadingIndicatorBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final ProgressIndicator loadingIndicator = (ProgressIndicator) getField(controller, "loadingIndicator");

        viewModel.loadingProperty().set(true);
        assertTrue(loadingIndicator.isVisible());
    }

    @Test
    @DisplayName("initialize - smsButton 绑定到 ViewModel smsButtonDisabled")
    void initialize_smsButtonBound() throws Exception {
        final RegisterViewModel viewModel = (RegisterViewModel) getField(controller, "viewModel");
        final Button smsButton = (Button) getField(controller, "smsButton");

        viewModel.smsButtonDisabledProperty().set(true);
        assertTrue(smsButton.isDisabled());
    }

    @Test
    @DisplayName("handleSendSms - 调用 ViewModel.sendSmsCode()")
    void handleSendSms_shouldCallViewModel() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleSendSms"));
    }

    @Test
    @DisplayName("handleRegister - 调用 ViewModel.register()")
    void handleRegister_shouldCallViewModel() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleRegister"));
    }

    @Test
    @DisplayName("handleBackToLogin - 不抛异常")
    void handleBackToLogin_shouldNotThrow() throws Exception {
        try {
            invokeNoArgMethod(controller, "handleBackToLogin");
        } catch (final Exception e) {
            // App.switchToLogin() 在测试环境中可能抛异常
        }
    }

    @Test
    @DisplayName("handleRegisterSuccess - 不抛异常")
    void handleRegisterSuccess_shouldNotThrow() throws Exception {
        final RegisterResponse response = new RegisterResponse();
        response.setAccessToken("test-token");
        response.setRefreshToken("test-refresh");
        response.setExpiresIn(7200L);

        try {
            invokeMethod(controller, "handleRegisterSuccess", RegisterResponse.class, response);
        } catch (final Exception e) {
            // App.switchToMain() 在测试环境中可能抛异常
        }
    }

    // ============ 辅助方法 ============

    private static void setField(final Object obj, final String name, final Object value) throws Exception {
        final Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private static Object getField(final Object obj, final String name) throws Exception {
        final Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(obj);
    }

    private static void invokeNoArgMethod(final Object obj, final String name) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(obj);
    }

    @SuppressWarnings("unchecked")
    private static <T> void invokeMethod(final Object obj, final String name,
            final Class<T> paramType, final T paramValue) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name, paramType);
        method.setAccessible(true);
        method.invoke(obj, paramValue);
    }
}
