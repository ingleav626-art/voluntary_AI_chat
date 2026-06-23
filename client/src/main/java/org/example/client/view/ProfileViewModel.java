package org.example.client.view;

import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;

import org.example.client.model.ChangePasswordRequest;
import org.example.client.model.ChangePhoneRequest;
import org.example.client.model.SmsSendRequest;
import org.example.client.model.UserInfo;
import org.example.client.service.AuthService;
import org.example.client.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 个人设置视图模型（MVVM）
 *
 * <p>管理用户个人信息状态、密码修改、手机号修改等操作。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ProfileViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileViewModel.class);

    /** 手机号正则表达式 */
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** 用户名最小长度 */
    private static final int USERNAME_MIN_LENGTH = 2;

    /** 用户名最大长度 */
    private static final int USERNAME_MAX_LENGTH = 50;

    /** 密码最小长度 */
    private static final int PASSWORD_MIN_LENGTH = 6;

    /** 密码最大长度 */
    private static final int PASSWORD_MAX_LENGTH = 50;

    /** 验证码长度 */
    private static final int SMS_CODE_LENGTH = 6;

    /** 验证码倒计时秒数 */
    private static final int COUNTDOWN_SECONDS = 60;

    // ========== 用户信息属性 ==========

    /** 用户ID */
    private final LongProperty userId = new SimpleLongProperty(0);

    /** 手机号（脱敏） */
    private final StringProperty phone = new SimpleStringProperty("");

    /** 用户名 */
    private final StringProperty username = new SimpleStringProperty("");

    /** 头像URL */
    private final StringProperty avatar = new SimpleStringProperty("");

    /** 个人简介 */
    private final StringProperty bio = new SimpleStringProperty("");

    /** 性别：0-未知，1-男，2-女 */
    private final IntegerProperty gender = new SimpleIntegerProperty(0);

    /** 年龄 */
    private final IntegerProperty age = new SimpleIntegerProperty(0);

    /** 生日 */
    private final ObjectProperty<java.time.LocalDate> birthday = new SimpleObjectProperty<>();

    /** 个人详细说明 */
    private final StringProperty detailBio = new SimpleStringProperty("");

    /** 注册时间 */
    private final ObjectProperty<java.time.LocalDateTime> createTime = new SimpleObjectProperty<>();

    // ========== 表单状态属性 ==========

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 成功消息 */
    private final StringProperty successMessage = new SimpleStringProperty("");

    // ========== 修改密码表单属性 ==========

    /** 密码验证码 */
    private final StringProperty passwordSmsCode = new SimpleStringProperty("");

    /** 新密码 */
    private final StringProperty newPassword = new SimpleStringProperty("");

    /** 确认密码 */
    private final StringProperty confirmPassword = new SimpleStringProperty("");

    // ========== 修改手机号表单属性 ==========

    /** 当前手机验证码 */
    private final StringProperty currentPhoneSmsCode = new SimpleStringProperty("");

    /** 新手机号 */
    private final StringProperty newPhone = new SimpleStringProperty("");

    /** 新手机验证码 */
    private final StringProperty newPhoneSmsCode = new SimpleStringProperty("");

    // ========== 回调 ==========

    /** 成功回调 */
    private Consumer<String> onSuccess;

    /** 失败回调 */
    private Consumer<String> onFailure;

    /** 信息刷新回调 */
    private Consumer<UserInfo> onProfileUpdated;

    /**
     * 加载用户信息
     */
    public void loadProfile() {
        loading.set(true);
        errorMessage.set("");

        UserService.getInstance().getProfile()
                .thenAcceptAsync(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        final UserInfo user = response.getData();
                        if (user != null) {
                            updateUserInfo(user);
                            LOG.info("用户信息加载成功: userId={}", user.getUserId());
                        }
                    } else {
                        final String msg = response != null ? response.getMessage() : "获取用户信息失败";
                        errorMessage.set(msg);
                        LOG.warn("获取用户信息失败: {}", msg);
                        if (onFailure != null) {
                            onFailure.accept(msg);
                        }
                    }
                });
    }

    /**
     * 更新用户信息属性
     *
     * @param user 用户信息
     */
    private void updateUserInfo(final UserInfo user) {
        userId.set(user.getUserId());
        phone.set(user.getPhone());
        username.set(user.getUsername());
        avatar.set(user.getAvatar());
        bio.set(user.getBio() != null ? user.getBio() : "");
        gender.set(user.getGender() != null ? user.getGender() : 0);
        age.set(user.getAge() != null ? user.getAge() : 0);
        birthday.set(user.getBirthday());
        detailBio.set(user.getDetailBio() != null ? user.getDetailBio() : "");
        createTime.set(user.getCreateTime());
    }

    /**
     * 保存用户信息
     */
    public void saveProfile() {
        errorMessage.set("");
        successMessage.set("");

        // 验证用户名
        final String usernameValue = username.get();
        if (usernameValue != null && !usernameValue.trim().isEmpty()) {
            if (usernameValue.length() < USERNAME_MIN_LENGTH || usernameValue.length() > USERNAME_MAX_LENGTH) {
                errorMessage.set("用户名长度需" + USERNAME_MIN_LENGTH + "-" + USERNAME_MAX_LENGTH + "个字符");
                return;
            }
        }

        loading.set(true);

        // 构建用户信息对象
        final UserInfo profile = new UserInfo();
        profile.setUserId(userId.get());
        profile.setUsername(username.get());
        profile.setAvatar(avatar.get());
        profile.setBio(bio.get());
        profile.setGender(gender.get());
        profile.setAge(age.get());
        profile.setBirthday(birthday.get());
        profile.setDetailBio(detailBio.get());

        UserService.getInstance().updateProfileFull(profile)
                .thenAcceptAsync(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("修改成功");
                        LOG.info("用户信息修改成功");
                        if (onSuccess != null) {
                            onSuccess.accept("修改成功");
                        }
                        if (onProfileUpdated != null) {
                            onProfileUpdated.accept(profile);
                        }
                    } else {
                        final String msg = response != null ? response.getMessage() : "修改失败";
                        errorMessage.set(msg);
                        LOG.warn("用户信息修改失败: {}", msg);
                        if (onFailure != null) {
                            onFailure.accept(msg);
                        }
                    }
                })
                .exceptionally(throwable -> {
                    loading.set(false);
                    final String msg = "保存失败: " + (throwable.getCause() != null
                            ? throwable.getCause().getMessage() : throwable.getMessage());
                    errorMessage.set(msg);
                    LOG.error("用户信息修改异常: {}", msg, throwable);
                    if (onFailure != null) {
                        onFailure.accept(msg);
                    }
                    return null;
                });
    }

    /**
     * 发送验证码（用于密码修改）
     *
     * @param sendButton 发送按钮（用于倒计时显示）
     */
    public void sendSmsCodeForPassword(final Button sendButton) {
        errorMessage.set("");

        // 使用当前手机号发送验证码
        final String phoneValue = phone.get();
        if (phoneValue == null || phoneValue.isEmpty()) {
            errorMessage.set("请先加载用户信息");
            return;
        }

        // 获取真实手机号（去除脱敏）
        // 注意：后端会根据当前登录用户自动获取手机号
        final org.example.client.model.SmsSendRequest smsRequest =
                new org.example.client.model.SmsSendRequest(phoneValue.replaceAll("\\*", ""));
        AuthService.getInstance().sendSmsCode(smsRequest)
                .thenAcceptAsync(response -> {
                    if (response != null && response.isSuccess()) {
                        successMessage.set("验证码已发送");
                        startCountdown(sendButton);
                        LOG.info("验证码发送成功");
                    } else {
                        final String msg = response != null ? response.getMessage() : "验证码发送失败";
                        errorMessage.set(msg);
                        LOG.warn("验证码发送失败: {}", msg);
                    }
                });
    }

    /**
     * 发送验证码（用于手机号修改 - 当前手机）
     *
     * @param sendButton 发送按钮
     */
    public void sendSmsCodeForCurrentPhone(final Button sendButton) {
        errorMessage.set("");

        final String phoneValue = phone.get();
        if (phoneValue == null || phoneValue.isEmpty()) {
            errorMessage.set("请先加载用户信息");
            return;
        }

        AuthService.getInstance().sendSmsCode(
                        new org.example.client.model.SmsSendRequest(phoneValue.replaceAll("\\*", "")))
                .thenAcceptAsync(response -> {
                    if (response != null && response.isSuccess()) {
                        successMessage.set("验证码已发送到当前手机");
                        startCountdown(sendButton);
                        LOG.info("当前手机验证码发送成功");
                    } else {
                        final String msg = response != null ? response.getMessage() : "验证码发送失败";
                        errorMessage.set(msg);
                        LOG.warn("当前手机验证码发送失败: {}", msg);
                    }
                });
    }

    /**
     * 发送验证码（用于手机号修改 - 新手机）
     *
     * @param sendButton 发送按钮
     */
    public void sendSmsCodeForNewPhone(final Button sendButton) {
        errorMessage.set("");

        final String newPhoneValue = newPhone.get();
        if (newPhoneValue == null || newPhoneValue.trim().isEmpty()) {
            errorMessage.set("请输入新手机号");
            return;
        }

        if (!newPhoneValue.matches(PHONE_REGEX)) {
            errorMessage.set("新手机号格式不正确");
            return;
        }

        AuthService.getInstance().sendSmsCode(
                        new org.example.client.model.SmsSendRequest(newPhoneValue))
                .thenAcceptAsync(response -> {
                    if (response != null && response.isSuccess()) {
                        successMessage.set("验证码已发送到新手机");
                        startCountdown(sendButton);
                        LOG.info("新手机验证码发送成功: {}", newPhoneValue);
                    } else {
                        final String msg = response != null ? response.getMessage() : "验证码发送失败";
                        errorMessage.set(msg);
                        LOG.warn("新手机验证码发送失败: {}", msg);
                    }
                });
    }

    /**
     * 开始倒计时
     *
     * @param button 按钮
     */
    private void startCountdown(final Button button) {
        button.setDisable(true);
        final java.util.concurrent.atomic.AtomicInteger seconds =
                new java.util.concurrent.atomic.AtomicInteger(COUNTDOWN_SECONDS);

        final javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                    final int remaining = seconds.decrementAndGet();
                    button.setText(remaining + "秒");
                    if (remaining <= 0) {
                        button.setDisable(false);
                        button.setText("发送验证码");
                    }
                }));
        timeline.setCycleCount(COUNTDOWN_SECONDS);
        timeline.play();
    }

    /**
     * 修改密码
     */
    public void changePassword() {
        errorMessage.set("");
        successMessage.set("");

        // 验证验证码
        final String smsCodeValue = passwordSmsCode.get();
        if (smsCodeValue == null || smsCodeValue.length() != SMS_CODE_LENGTH) {
            errorMessage.set("请输入6位验证码");
            return;
        }

        // 验证新密码
        final String newPasswordValue = newPassword.get();
        if (newPasswordValue == null || newPasswordValue.length() < PASSWORD_MIN_LENGTH) {
            errorMessage.set("密码长度至少" + PASSWORD_MIN_LENGTH + "位");
            return;
        }

        if (newPasswordValue.length() > PASSWORD_MAX_LENGTH) {
            errorMessage.set("密码长度不能超过" + PASSWORD_MAX_LENGTH + "位");
            return;
        }

        // 验证确认密码
        final String confirmPasswordValue = confirmPassword.get();
        if (!newPasswordValue.equals(confirmPasswordValue)) {
            errorMessage.set("两次密码不一致");
            return;
        }

        loading.set(true);

        final ChangePasswordRequest request = new ChangePasswordRequest(
                smsCodeValue, newPasswordValue, confirmPasswordValue);

        UserService.getInstance().changePassword(request)
                .thenAcceptAsync(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("密码修改成功");
                        // 清空表单
                        passwordSmsCode.set("");
                        newPassword.set("");
                        confirmPassword.set("");
                        LOG.info("密码修改成功");
                        if (onSuccess != null) {
                            onSuccess.accept("密码修改成功");
                        }
                    } else {
                        final String msg = response != null ? response.getMessage() : "密码修改失败";
                        errorMessage.set(msg);
                        LOG.warn("密码修改失败: {}", msg);
                        if (onFailure != null) {
                            onFailure.accept(msg);
                        }
                    }
                });
    }

    /**
     * 修改手机号
     */
    public void changePhone() {
        errorMessage.set("");
        successMessage.set("");

        // 验证当前手机验证码
        final String currentSmsCodeValue = currentPhoneSmsCode.get();
        if (currentSmsCodeValue == null || currentSmsCodeValue.length() != SMS_CODE_LENGTH) {
            errorMessage.set("请输入当前手机的6位验证码");
            return;
        }

        // 验证新手机号
        final String newPhoneValue = newPhone.get();
        if (newPhoneValue == null || !newPhoneValue.matches(PHONE_REGEX)) {
            errorMessage.set("新手机号格式不正确");
            return;
        }

        // 验证新手机验证码
        final String newSmsCodeValue = newPhoneSmsCode.get();
        if (newSmsCodeValue == null || newSmsCodeValue.length() != SMS_CODE_LENGTH) {
            errorMessage.set("请输入新手机的6位验证码");
            return;
        }

        loading.set(true);

        final ChangePhoneRequest request = new ChangePhoneRequest(
                currentSmsCodeValue, newPhoneValue, newSmsCodeValue);

        UserService.getInstance().changePhone(request)
                .thenAcceptAsync(response -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("手机号修改成功");
                        // 更新显示的手机号（脱敏）
                        phone.set(maskPhone(newPhoneValue));
                        // 清空表单
                        currentPhoneSmsCode.set("");
                        newPhone.set("");
                        newPhoneSmsCode.set("");
                        LOG.info("手机号修改成功: {}", newPhoneValue);
                        if (onSuccess != null) {
                            onSuccess.accept("手机号修改成功");
                        }
                    } else {
                        final String msg = response != null ? response.getMessage() : "手机号修改失败";
                        errorMessage.set(msg);
                        LOG.warn("手机号修改失败: {}", msg);
                        if (onFailure != null) {
                            onFailure.accept(msg);
                        }
                    }
                });
    }

    /**
     * 手机号脱敏
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    private String maskPhone(final String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    // ========== Property getters ==========

    public LongProperty userIdProperty() {
        return userId;
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public StringProperty avatarProperty() {
        return avatar;
    }

    public StringProperty bioProperty() {
        return bio;
    }

    public IntegerProperty genderProperty() {
        return gender;
    }

    public IntegerProperty ageProperty() {
        return age;
    }

    public ObjectProperty<java.time.LocalDate> birthdayProperty() {
        return birthday;
    }

    public StringProperty detailBioProperty() {
        return detailBio;
    }

    public ObjectProperty<java.time.LocalDateTime> createTimeProperty() {
        return createTime;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public StringProperty passwordSmsCodeProperty() {
        return passwordSmsCode;
    }

    public StringProperty newPasswordProperty() {
        return newPassword;
    }

    public StringProperty confirmPasswordProperty() {
        return confirmPassword;
    }

    public StringProperty currentPhoneSmsCodeProperty() {
        return currentPhoneSmsCode;
    }

    public StringProperty newPhoneProperty() {
        return newPhone;
    }

    public StringProperty newPhoneSmsCodeProperty() {
        return newPhoneSmsCode;
    }

    // ========== Callback setters ==========

    public void setOnSuccess(final Consumer<String> callback) {
        this.onSuccess = callback;
    }

    public void setOnFailure(final Consumer<String> callback) {
        this.onFailure = callback;
    }

    public void setOnProfileUpdated(final Consumer<UserInfo> callback) {
        this.onProfileUpdated = callback;
    }
}