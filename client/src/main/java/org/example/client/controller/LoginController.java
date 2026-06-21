package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.example.client.model.LoginResponse;
import org.example.client.view.LoginViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录界面控制器
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class LoginController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private TextField phoneField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox rememberMeCheckBox;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    @FXML
    private ProgressIndicator loadingIndicator;

    private LoginViewModel viewModel;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        viewModel = new LoginViewModel();

        // 双向绑定 UI 控件与 ViewModel
        phoneField.textProperty().bindBidirectional(viewModel.phoneProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        rememberMeCheckBox.selectedProperty().bindBidirectional(viewModel.rememberMeProperty());

        // 单向绑定错误提示和加载状态
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        loginButton.disableProperty().bind(viewModel.loadingProperty());
        loadingIndicator.visibleProperty().bind(viewModel.loadingProperty());

        // 设置登录回调
        viewModel.setOnSuccess(this::handleLoginSuccess);
        viewModel.setOnFailure(this::handleLoginFailure);

        LOG.info("登录控制器初始化完成");
    }

    @FXML
    private void handleLogin() {
        LOG.debug("点击登录按钮");
        viewModel.login();
    }

    @FXML
    private void handleKeyPress(final KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            LOG.debug("按下 Enter 键登录");
            viewModel.login();
        }
    }

    @FXML
    private void handleForgotPassword() {
        LOG.info("点击忘记密码");
        // TODO: 跳转到忘记密码页面
    }

    @FXML
    private void handleRegister() {
        LOG.info("点击注册，跳转到注册页面");
        org.example.client.App.switchToRegister();
    }

    private void handleLoginSuccess(final LoginResponse response) {
        Platform.runLater(() -> {
            LOG.info("登录成功，准备跳转主界面");
            org.example.client.App.switchToMain(response);
        });
    }

    private void handleLoginFailure(final String message) {
        Platform.runLater(() -> {
            LOG.warn("登录失败: {}", message);
            // 错误消息已通过绑定自动显示
        });
    }
}

