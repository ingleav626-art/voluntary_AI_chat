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
import org.example.client.view.ForgotPasswordViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 忘记密码界面控制器
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ForgotPasswordController implements Initializable {

  private static final Logger LOG = LoggerFactory.getLogger(ForgotPasswordController.class);

  /** 重置成功后返回登录页的延迟时间（毫秒） */
  private static final long RETURN_DELAY_MS = 1500L;

  @FXML
  private TextField phoneField;

  @FXML
  private Button smsButton;

  @FXML
  private TextField codeField;

  @FXML
  private PasswordField newPasswordField;

  @FXML
  private PasswordField confirmPasswordField;

  @FXML
  private Button resetButton;

  @FXML
  private Label errorLabel;

  @FXML
  private Label successLabel;

  @FXML
  private ProgressIndicator loadingIndicator;

  private ForgotPasswordViewModel viewModel;

  @Override
  public void initialize(final URL location, final ResourceBundle resources) {
    viewModel = new ForgotPasswordViewModel();

    // 双向绑定 UI 控件与 ViewModel
    phoneField.textProperty().bindBidirectional(viewModel.phoneProperty());
    codeField.textProperty().bindBidirectional(viewModel.codeProperty());
    newPasswordField.textProperty().bindBidirectional(viewModel.newPasswordProperty());
    confirmPasswordField.textProperty().bindBidirectional(viewModel.confirmPasswordProperty());

    // 单向绑定状态
    errorLabel.textProperty().bind(viewModel.errorMessageProperty());
    successLabel.textProperty().bind(viewModel.successMessageProperty());
    resetButton.disableProperty().bind(viewModel.loadingProperty());
    loadingIndicator.visibleProperty().bind(viewModel.loadingProperty());
    smsButton.disableProperty().bind(viewModel.smsButtonDisabledProperty());
    smsButton.textProperty().bind(viewModel.smsButtonTextProperty());

    // 设置回调
    viewModel.setOnResetSuccess(this::handleResetSuccess);

    LOG.info("忘记密码控制器初始化完成");
  }

  @FXML
  private void handleSendSms() {
    LOG.debug("点击获取验证码");
    viewModel.sendSmsCode();
  }

  @FXML
  private void handleResetPassword() {
    LOG.debug("点击重置密码按钮");
    viewModel.resetPassword();
  }

  @FXML
  private void handleBackToLogin() {
    LOG.info("点击返回登录");
    org.example.client.App.switchToLogin();
  }

  private void handleResetSuccess(final String phone) {
    Platform.runLater(() -> {
      LOG.info("密码重置成功，延迟返回登录页");
      // 延迟返回登录页，让用户看到成功提示
      new Thread(() -> {
        try {
          Thread.sleep(RETURN_DELAY_MS);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        Platform.runLater(() -> org.example.client.App.switchToLoginWithPhone(phone));
      }).start();
    });
  }
}
