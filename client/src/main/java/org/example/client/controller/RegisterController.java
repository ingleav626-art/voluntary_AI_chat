package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import org.example.client.model.RegisterResponse;
import org.example.client.view.RegisterViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 注册界面控制器
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class RegisterController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterController.class);

    /** 注册成功后返回登录页的延迟时间（毫秒） */
    private static final long RETURN_DELAY_MS = 1000L;

    @FXML
    private TextField phoneField;

    @FXML
    private Button smsButton;

    @FXML
    private TextField codeField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button registerButton;

    @FXML
    private Label errorLabel;

    @FXML
    private Label successLabel;

    @FXML
    private ProgressIndicator loadingIndicator;

    private RegisterViewModel viewModel;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        viewModel = new RegisterViewModel();

        // 双向绑定 UI 控件与 ViewModel
        phoneField.textProperty().bindBidirectional(viewModel.phoneProperty());
        codeField.textProperty().bindBidirectional(viewModel.codeProperty());
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        confirmPasswordField.textProperty().bindBidirectional(viewModel.confirmPasswordProperty());

        // 单向绑定状态
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        successLabel.textProperty().bind(viewModel.successMessageProperty());
        registerButton.disableProperty().bind(viewModel.loadingProperty());
        loadingIndicator.visibleProperty().bind(viewModel.loadingProperty());
        smsButton.disableProperty().bind(viewModel.smsButtonDisabledProperty());
        smsButton.textProperty().bind(viewModel.smsButtonTextProperty());

        // 设置回调
        viewModel.setOnRegisterSuccess(this::handleRegisterSuccess);
        viewModel.setOnBackToLogin(this::handleBackToLogin);

        LOG.info("注册控制器初始化完成");
    }

    @FXML
    private void handleSendSms() {
        LOG.debug("点击获取验证码");
        viewModel.sendSmsCode();
    }

    @FXML
    private void handleRegister() {
        LOG.debug("点击注册按钮");
        viewModel.register();
    }

    @FXML
    private void handleBackToLogin() {
        LOG.info("点击返回登录");
        // 由 App 统一处理页面跳转
        org.example.client.App.switchToLogin();
    }

    private void handleRegisterSuccess(final RegisterResponse response) {
        Platform.runLater(() -> {
            LOG.info("注册成功，准备返回登录页面");
            // 延迟1秒后返回登录页面，让用户看到成功提示
            new Thread(() -> {
                try {
                    Thread.sleep(RETURN_DELAY_MS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Platform.runLater(() -> org.example.client.App.switchToLogin());
            }).start();
        });
    }
}
