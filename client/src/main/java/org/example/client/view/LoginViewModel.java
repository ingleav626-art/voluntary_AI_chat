package org.example.client.view;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.example.client.config.ServerConnectionManager;
import org.example.client.config.ServerMode;
import org.example.client.engine.JdbcUserRepository;
import org.example.client.engine.LocalAiEngine;
import org.example.client.model.LoginRequest;
import org.example.client.model.LoginResponse;
import org.example.client.model.UserInfo;
import org.example.client.service.AuthService;
import org.example.client.util.CredentialStorage;
import org.example.client.util.TokenStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录视图模型（MVVM）
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class LoginViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(LoginViewModel.class);

    /** 手机号正则表达式 */
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** 密码最小长度 */
    private static final int PASSWORD_MIN_LENGTH = 6;

    /** 手机号属性 */
    private final StringProperty phone = new SimpleStringProperty("");

    /** 密码属性 */
    private final StringProperty password = new SimpleStringProperty("");

    /** 记住我属性 */
    private final BooleanProperty rememberMe = new SimpleBooleanProperty(false);

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 成功回调 */
    private Consumer<LoginResponse> onSuccess;

    /** 失败回调 */
    private Consumer<String> onFailure;

    /**
     * 执行登录
     *
     * <p>
     * 本地模式时优先尝试云端登录，云端不可用时自动回退到本地 H2 密码验证。
     * 热点/云端模式仅走云端登录。
     * </p>
     */
    public void login() {
        // 清空错误消息
        errorMessage.set("");

        // 验证输入
        if (!validateInput()) {
            return;
        }

        // 设置加载状态
        loading.set(true);

        final String phoneValue = phone.get();
        final String passwordValue = password.get();
        final boolean rememberMeValue = rememberMe.get();

        // 构建请求
        final LoginRequest request = new LoginRequest(
                phoneValue, passwordValue, rememberMeValue);

        // 发送云端登录请求
        AuthService.getInstance().login(request)
                .thenAcceptAsync(response -> {
                    if (response != null && response.isSuccess()) {
                        // 云端登录成功
                        Platform.runLater(() -> {
                            loading.set(false);
                            handleSuccess(response.getData());
                        });
                    } else {
                        // 云端登录失败，检查是否可本地回退
                        if (ServerConnectionManager.getInstance().getCurrentMode() == ServerMode.LOCAL) {
                            // 本地模式：尝试本地 H2 登录兜底
                            tryLocalLogin(phoneValue, passwordValue, rememberMeValue);
                        } else {
                            // 热点/云端模式：无回退，显示云端错误
                            Platform.runLater(() -> {
                                loading.set(false);
                                handleFailure(response != null ? response.getMessage() : "登录失败");
                            });
                        }
                    }
                })
                .exceptionally(ex -> {
                    // 网络异常（云端不可达）
                    LOG.warn("云端登录网络异常", ex);
                    if (ServerConnectionManager.getInstance().getCurrentMode() == ServerMode.LOCAL) {
                        tryLocalLogin(phoneValue, passwordValue, rememberMeValue);
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
     * 尝试本地 H2 密码登录（云端不可用时兜底）
     */
    private void tryLocalLogin(final String phone, final String password, final boolean rememberMe) {
        try {
            LOG.info("尝试本地 H2 登录兜底: phone={}", phone);
            final JdbcUserRepository.LocalUser localUser = LocalAiEngine.getInstance().loginLocal(phone, password);

            if (localUser != null) {
                Platform.runLater(() -> {
                    loading.set(false);
                    LOG.info("本地登录成功: userId={}, username={}", localUser.getId(), localUser.getUsername());

                    // 构造本地 LoginResponse（无云端 Token，本地可用）
                    final UserInfo userInfo = new UserInfo();
                    userInfo.setUserId(localUser.getId());
                    userInfo.setUsername(localUser.getUsername());
                    userInfo.setPhone(localUser.getPhone());
                    userInfo.setAvatar(localUser.getAvatar());

                    final LoginResponse localResponse = new LoginResponse(
                            "local_token_" + localUser.getId(),
                            "local_refresh_" + localUser.getId(),
                            86400L, // 24h
                            userInfo);

                    handleSuccess(localResponse);
                });
            } else {
                Platform.runLater(() -> {
                    loading.set(false);
                    handleFailure("手机号或密码错误");
                });
            }
        } catch (final Exception e) {
            LOG.error("本地登录异常", e);
            Platform.runLater(() -> {
                loading.set(false);
                handleFailure("本地登录失败，请稍后重试");
            });
        }
    }

    private boolean validateInput() {
        final String phoneValue = phone.get();
        final String passwordValue = password.get();

        if (phoneValue == null || phoneValue.trim().isEmpty()) {
            errorMessage.set("请输入手机号");
            return false;
        }

        if (!phoneValue.matches(PHONE_REGEX)) {
            errorMessage.set("手机号格式错误");
            return false;
        }

        if (passwordValue == null || passwordValue.trim().isEmpty()) {
            errorMessage.set("请输入密码");
            return false;
        }

        if (passwordValue.length() < PASSWORD_MIN_LENGTH) {
            errorMessage.set("密码长度至少6位");
            return false;
        }

        return true;
    }

    private void handleSuccess(final LoginResponse response) {
        LOG.info("[记住我-登录成功] 登录成功, rememberMe={}, phone={}", rememberMe.get(), phone.get());

        // 无论是否勾选"记住我"，都保存 Token 到内存
        // 只有勾选"记住我"时才持久化 Token 和凭证到文件
        TokenStorage.save(response, rememberMe.get());

        // 勾选"记住我"时，保存账号密码用于下次预填表单
        if (rememberMe.get()) {
            LOG.info("[记住我-登录成功] 勾选了记住我，开始保存凭证...");
            CredentialStorage.save(phone.get(), password.get());
        } else {
            // 未勾选时清除旧凭证，防止残留
            LOG.info("[记住我-登录成功] 未勾选记住我，清除旧凭证");
            CredentialStorage.clear();
        }

        // 调用成功回调
        if (onSuccess != null) {
            onSuccess.accept(response);
        }
    }

    private void handleFailure(final String message) {
        LOG.warn("登录失败: {}", message);
        errorMessage.set(message);

        // 调用失败回调
        if (onFailure != null) {
            onFailure.accept(message);
        }
    }

    // Property getters
    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty passwordProperty() {
        return password;
    }

    public BooleanProperty rememberMeProperty() {
        return rememberMe;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    // Callback setters
    public void setOnSuccess(final Consumer<LoginResponse> callback) {
        this.onSuccess = callback;
    }

    public void setOnFailure(final Consumer<String> callback) {
        this.onFailure = callback;
    }
}
