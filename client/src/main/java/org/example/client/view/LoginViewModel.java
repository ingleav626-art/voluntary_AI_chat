package org.example.client.view;

import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.example.client.model.LoginRequest;
import org.example.client.model.LoginResponse;
import org.example.client.service.AuthService;
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

        // 构建请求
        final LoginRequest request = new LoginRequest(
                phone.get(),
                password.get(),
                rememberMe.get()
        );

        // 发送异步请求
        AuthService.getInstance().login(request)
                .thenAcceptAsync(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        handleSuccess(response.getData());
                    } else {
                        handleFailure(response != null ? response.getMessage() : "登录失败");
                    }
                });
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
        LOG.info("登录成功");

        // 无论是否勾选"记住我"，都保存 Token 到内存
        // 只有勾选"记住我"时才持久化到文件
        TokenStorage.save(response, rememberMe.get());

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

