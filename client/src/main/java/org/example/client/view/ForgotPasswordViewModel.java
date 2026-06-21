package org.example.client.view;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.example.client.model.ForgotPasswordRequest;
import org.example.client.model.SmsSendRequest;
import org.example.client.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 忘记密码视图模型（MVVM）
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ForgotPasswordViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(ForgotPasswordViewModel.class);

    /** 手机号正则表达式 */
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    /** 密码最小长度 */
    private static final int PASSWORD_MIN_LENGTH = 6;

    /** 密码最大长度 */
    private static final int PASSWORD_MAX_LENGTH = 50;

    /** 验证码倒计时秒数 */
    private static final int COUNTDOWN_SECONDS = 60;

    /** 倒计时间隔（毫秒） */
    private static final long COUNTDOWN_INTERVAL_MS = 1000L;

    /** 手机号属性 */
    private final StringProperty phone = new SimpleStringProperty("");

    /** 验证码属性 */
    private final StringProperty code = new SimpleStringProperty("");

    /** 新密码属性 */
    private final StringProperty newPassword = new SimpleStringProperty("");

    /** 确认密码属性 */
    private final StringProperty confirmPassword = new SimpleStringProperty("");

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 验证码按钮禁用状态 */
    private final BooleanProperty smsButtonDisabled = new SimpleBooleanProperty(false);

    /** 验证码按钮文本 */
    private final StringProperty smsButtonText = new SimpleStringProperty("获取验证码");

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 成功消息 */
    private final StringProperty successMessage = new SimpleStringProperty("");

    /** 重置成功回调 */
    private Consumer<String> onResetSuccess;

    /** 返回登录回调 */
    private Runnable onBackToLogin;

    /** 倒计时线程 */
    private Thread countdownThread;

    /**
     * 发送验证码
     */
    public void sendSmsCode() {
        errorMessage.set("");
        successMessage.set("");

        final String phoneValue = phone.get();
        if (phoneValue == null || phoneValue.trim().isEmpty()) {
            errorMessage.set("请输入手机号");
            return;
        }

        if (!phoneValue.matches(PHONE_REGEX)) {
            errorMessage.set("手机号格式错误");
            return;
        }

        final SmsSendRequest request = new SmsSendRequest(phoneValue);

        AuthService.getInstance().sendSmsCode(request)
                .thenAcceptAsync(response -> Platform.runLater(() -> {
                    if (response != null && response.isSuccess()) {
                        successMessage.set("验证码已发送");
                        startCountdown();
                    } else {
                        errorMessage.set(response != null ? response.getMessage() : "验证码发送失败");
                    }
                }));
    }

    /**
     * 执行密码重置
     */
    public void resetPassword() {
        errorMessage.set("");
        successMessage.set("");

        if (!validateInput()) {
            return;
        }

        loading.set(true);

        final ForgotPasswordRequest request = new ForgotPasswordRequest(
                phone.get(),
                code.get(),
                newPassword.get(),
                confirmPassword.get());

        AuthService.getInstance().forgotPassword(request)
                .thenAcceptAsync(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        handleSuccess();
                    } else {
                        handleFailure(response != null ? response.getMessage() : "密码重置失败");
                    }
                });
    }

    private boolean validateInput() {
        final String phoneValue = phone.get();
        final String codeValue = code.get();
        final String passwordValue = newPassword.get();
        final String confirmValue = confirmPassword.get();

        if (phoneValue == null || !phoneValue.matches(PHONE_REGEX)) {
            errorMessage.set("手机号格式错误");
            return false;
        }

        if (codeValue == null || codeValue.length() != CODE_LENGTH) {
            errorMessage.set("验证码为6位数字");
            return false;
        }

        if (passwordValue == null || passwordValue.length() < PASSWORD_MIN_LENGTH
                || passwordValue.length() > PASSWORD_MAX_LENGTH) {
            errorMessage.set("密码长度6-50个字符");
            return false;
        }

        if (!passwordValue.equals(confirmValue)) {
            errorMessage.set("两次密码不一致");
            return false;
        }

        return true;
    }

    /**
     * 启动验证码倒计时
     */
    private void startCountdown() {
        smsButtonDisabled.set(true);

        countdownThread = new Thread(() -> {
            for (int i = COUNTDOWN_SECONDS; i > 0; i--) {
                final int seconds = i;
                Platform.runLater(() -> smsButtonText.set(seconds + "秒后重发"));
                try {
                    Thread.sleep(COUNTDOWN_INTERVAL_MS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            Platform.runLater(() -> {
                smsButtonText.set("获取验证码");
                smsButtonDisabled.set(false);
            });
        });
        countdownThread.setDaemon(true);
        countdownThread.start();
    }

    private void handleSuccess() {
        LOG.info("密码重置成功: phone={}", phone.get());
        successMessage.set("密码重置成功，请重新登录");

        if (onResetSuccess != null) {
            onResetSuccess.accept(phone.get());
        }
    }

    private void handleFailure(final String message) {
        LOG.warn("密码重置失败: {}", message);
        errorMessage.set(message);
    }

    // Property getters
    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty codeProperty() {
        return code;
    }

    public StringProperty newPasswordProperty() {
        return newPassword;
    }

    public StringProperty confirmPasswordProperty() {
        return confirmPassword;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty smsButtonDisabledProperty() {
        return smsButtonDisabled;
    }

    public StringProperty smsButtonTextProperty() {
        return smsButtonText;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    // Callback setters
    public void setOnResetSuccess(final Consumer<String> callback) {
        this.onResetSuccess = callback;
    }

    public void setOnBackToLogin(final Runnable callback) {
        this.onBackToLogin = callback;
    }
}
