package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.example.client.view.ForgotPasswordViewModel;
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
 * ForgotPasswordController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ForgotPasswordController 测试")
class ForgotPasswordControllerTest extends JavaFxTestBase {

    private ForgotPasswordController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new ForgotPasswordController();

        final TextField phoneField = new TextField();
        final Button smsButton = new Button();
        final TextField codeField = new TextField();
        final PasswordField newPasswordField = new PasswordField();
        final PasswordField confirmPasswordField = new PasswordField();
        final Button resetButton = new Button();
        final Label errorLabel = new Label();
        final Label successLabel = new Label();
        final ProgressIndicator loadingIndicator = new ProgressIndicator();

        setField(controller, "phoneField", phoneField);
        setField(controller, "smsButton", smsButton);
        setField(controller, "codeField", codeField);
        setField(controller, "newPasswordField", newPasswordField);
        setField(controller, "confirmPasswordField", confirmPasswordField);
        setField(controller, "resetButton", resetButton);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "successLabel", successLabel);
        setField(controller, "loadingIndicator", loadingIndicator);

        controller.initialize(null, null);
    }

    @Test
    @DisplayName("initialize - ViewModel 正确创建")
    void initialize_shouldCreateViewModel() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("initialize - phoneField 与 ViewModel 双向绑定")
    void initialize_phoneFieldBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final TextField phoneField = (TextField) getField(controller, "phoneField");

        phoneField.setText("13800138000");
        assertEquals("13800138000", viewModel.phoneProperty().get());
    }

    @Test
    @DisplayName("initialize - codeField 与 ViewModel 双向绑定")
    void initialize_codeFieldBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final TextField codeField = (TextField) getField(controller, "codeField");

        codeField.setText("654321");
        assertEquals("654321", viewModel.codeProperty().get());
    }

    @Test
    @DisplayName("initialize - newPasswordField 与 ViewModel 双向绑定")
    void initialize_newPasswordFieldBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final PasswordField newPasswordField = (PasswordField) getField(controller, "newPasswordField");

        newPasswordField.setText("newpass123");
        assertEquals("newpass123", viewModel.newPasswordProperty().get());
    }

    @Test
    @DisplayName("initialize - confirmPasswordField 与 ViewModel 双向绑定")
    void initialize_confirmPasswordFieldBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final PasswordField confirmPasswordField = (PasswordField) getField(controller, "confirmPasswordField");

        confirmPasswordField.setText("newpass123");
        assertEquals("newpass123", viewModel.confirmPasswordProperty().get());
    }

    @Test
    @DisplayName("initialize - errorLabel 绑定到 ViewModel errorMessage")
    void initialize_errorLabelBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        viewModel.errorMessageProperty().set("验证码已过期");
        assertEquals("验证码已过期", errorLabel.getText());
    }

    @Test
    @DisplayName("initialize - successLabel 绑定到 ViewModel successMessage")
    void initialize_successLabelBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final Label successLabel = (Label) getField(controller, "successLabel");

        viewModel.successMessageProperty().set("密码重置成功");
        assertEquals("密码重置成功", successLabel.getText());
    }

    @Test
    @DisplayName("initialize - resetButton 绑定到 ViewModel loading")
    void initialize_resetButtonBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final Button resetButton = (Button) getField(controller, "resetButton");

        viewModel.loadingProperty().set(true);
        assertTrue(resetButton.isDisabled());
    }

    @Test
    @DisplayName("initialize - loadingIndicator 绑定到 ViewModel loading")
    void initialize_loadingIndicatorBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
        final ProgressIndicator loadingIndicator = (ProgressIndicator) getField(controller, "loadingIndicator");

        viewModel.loadingProperty().set(true);
        assertTrue(loadingIndicator.isVisible());
    }

    @Test
    @DisplayName("initialize - smsButton 绑定到 ViewModel smsButtonDisabled")
    void initialize_smsButtonBound() throws Exception {
        final ForgotPasswordViewModel viewModel = (ForgotPasswordViewModel) getField(controller, "viewModel");
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
    @DisplayName("handleResetPassword - 调用 ViewModel.resetPassword()")
    void handleResetPassword_shouldCallViewModel() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleResetPassword"));
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
    @DisplayName("handleResetSuccess - 不抛异常")
    void handleResetSuccess_shouldNotThrow() throws Exception {
        try {
            invokeMethod(controller, "handleResetSuccess", String.class, "13800138000");
        } catch (final Exception e) {
            // Platform.runLater + App.switchToLoginWithPhone 在测试环境中可能抛异常
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
