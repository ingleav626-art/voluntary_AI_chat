package org.example.client.view;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.example.client.config.ServerConnectionManager;
import org.example.client.config.ServerMode;
import org.example.client.engine.LocalAiEngine;
import org.example.client.model.RegisterRequest;
import org.example.client.model.RegisterResponse;
import org.example.client.model.SmsSendRequest;
import org.example.client.model.UserInfo;
import org.example.client.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 注册视图模型（MVVM）
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class RegisterViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterViewModel.class);

    /** 手机号正则表达式 */
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    /** 用户名最小长度 */
    private static final int USERNAME_MIN_LENGTH = 2;

    /** 用户名最大长度 */
    private static final int USERNAME_MAX_LENGTH = 50;

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

    /** 用户名属性 */
    private final StringProperty username = new SimpleStringProperty("");

    /** 密码属性 */
    private final StringProperty password = new SimpleStringProperty("");

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

    /** 注册成功回调 */
    private Consumer<RegisterResponse> onRegisterSuccess;

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
                .thenAcceptAsync(response -> {
                    javafx.application.Platform.runLater(() -> {
                        if (response != null && response.isSuccess()) {
                            successMessage.set("验证码已发送");
                            startCountdown();
                        } else {
                            errorMessage.set(response != null ? response.getMessage() : "验证码发送失败");
                        }
                    });
                });
    }

    /**
     * 执行注册
     *
     * <p>
     * 本地模式时优先尝试云端注册（需短信验证码），云端不可用时自动回退到本地 H2 注册（无需短信）。
     * 热点/云端模式仅走云端注册。
     * </p>
     */
    public void register() {
        errorMessage.set("");
        successMessage.set("");

        if (!validateInput()) {
            return;
        }

        loading.set(true);

        final String phoneValue = phone.get();
        final String codeValue = code.get();
        final String usernameValue = username.get();
        final String passwordValue = password.get();

        // 本地模式时，如果云端不可达，可以跳过短信验证
        final boolean isLocalMode = ServerConnectionManager.getInstance()
                .getCurrentMode() == ServerMode.LOCAL;

        final RegisterRequest request = new RegisterRequest(
                phoneValue, codeValue, usernameValue, passwordValue);

        AuthService.getInstance().register(request)
                .thenAcceptAsync(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.isSuccess()) {
                            loading.set(false);
                            handleSuccess(response.getData());
                        } else if (isLocalMode) {
                            // 云端注册失败 + 本地模式 → 尝试本地注册
                            tryLocalRegister(phoneValue, usernameValue, passwordValue);
                        } else {
                            loading.set(false);
                            handleFailure(response != null ? response.getMessage() : "注册失败");
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.warn("云端注册网络异常", ex);
                    if (isLocalMode) {
                        tryLocalRegister(phoneValue, usernameValue, passwordValue);
                    } else {
                        Platform.runLater(() -> {
                            loading.set(false);
                            handleFailure("网络连接失败，请检查网络");
                        });
                    }
                    return null;
                });
    }

    /**
     * 本地注册兜底（无短信验证）
     */
    private void tryLocalRegister(final String phone, final String username, final String password) {
        try {
            LOG.info("尝试本地 H2 注册兜底: phone={}, username={}", phone, username);
            final Long userId = LocalAiEngine.getInstance().registerLocal(phone, username, password);

            if (userId != null) {
                Platform.runLater(() -> {
                    loading.set(false);
                    LOG.info("本地注册成功: userId={}", userId);

                    final UserInfo userInfo = new UserInfo();
                    userInfo.setUserId(userId);
                    userInfo.setUsername(username);
                    userInfo.setPhone(phone);

                    final RegisterResponse registerResponse = new RegisterResponse();
                    registerResponse.setUser(userInfo);

                    handleSuccess(registerResponse);
                });
            } else {
                Platform.runLater(() -> {
                    loading.set(false);
                    handleFailure("手机号或用户名已存在");
                });
            }
        } catch (final Exception e) {
            LOG.error("本地注册异常", e);
            Platform.runLater(() -> {
                loading.set(false);
                handleFailure("本地注册失败，请稍后重试");
            });
        }
    }

    private boolean validateInput() {
        final String phoneValue = phone.get();
        final String codeValue = code.get();
        final String usernameValue = username.get();
        final String passwordValue = password.get();
        final String confirmValue = confirmPassword.get();

        if (phoneValue == null || !phoneValue.matches(PHONE_REGEX)) {
            errorMessage.set("手机号格式错误");
            return false;
        }

        if (codeValue == null || codeValue.length() != CODE_LENGTH) {
            errorMessage.set("验证码为6位数字");
            return false;
        }

        if (usernameValue == null || usernameValue.length() < USERNAME_MIN_LENGTH
                || usernameValue.length() > USERNAME_MAX_LENGTH) {
            errorMessage.set("用户名长度2-50个字符");
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

    private void handleSuccess(final RegisterResponse response) {
        LOG.info("注册成功，自动登录: userId={}",
                response.getUser() != null ? response.getUser().getUserId() : null);
        successMessage.set("注册成功，正在登录...");

        if (onRegisterSuccess != null) {
            onRegisterSuccess.accept(response);
        }
    }

    private void handleFailure(final String message) {
        LOG.warn("注册失败: {}", message);
        errorMessage.set(message);
    }

    /**
     * 返回登录页面
     */
    public void backToLogin() {
        if (onBackToLogin != null) {
            onBackToLogin.run();
        }
    }

    // Property getters
    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty codeProperty() {
        return code;
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public StringProperty passwordProperty() {
        return password;
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
    public void setOnRegisterSuccess(final Consumer<RegisterResponse> callback) {
        this.onRegisterSuccess = callback;
    }

    public void setOnBackToLogin(final Runnable callback) {
        this.onBackToLogin = callback;
    }
}
