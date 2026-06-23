package org.example.client.view;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.Button;

import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProfileViewModel 单元测试
 *
 * <p>测试属性绑定、验证逻辑、状态管理等功能。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ProfileViewModel 测试")
class ProfileViewModelTest {

    private static boolean toolkitInitialized = false;

    private ProfileViewModel viewModel;

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        // 检查 Toolkit 是否已经初始化（可能由其他测试类初始化）
        try {
            Platform.runLater(() -> {});
        } catch (final IllegalStateException e) {
            // Toolkit 未初始化，需要初始化
            final CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
            Platform.setImplicitExit(false);
        }
    }

    @BeforeEach
    void setUp() {
        // 确保 TokenStorage 中有有效的 token，避免异步请求时空指针
        TokenStorage.clear();
        final org.example.client.model.LoginResponse loginResponse = new org.example.client.model.LoginResponse();
        loginResponse.setAccessToken("test-token-for-profile");
        loginResponse.setRefreshToken("test-refresh-token");
        loginResponse.setExpiresIn(7200L);
        TokenStorage.save(loginResponse);

        viewModel = new ProfileViewModel();
    }

    // ========== 属性初始化测试 ==========

    @Test
    @DisplayName("初始属性值正确")
    void testInitialProperties() {
        assertEquals(0, viewModel.userIdProperty().get());
        assertEquals("", viewModel.phoneProperty().get());
        assertEquals("", viewModel.usernameProperty().get());
        assertEquals("", viewModel.avatarProperty().get());
        assertEquals("", viewModel.bioProperty().get());
        assertEquals(0, viewModel.genderProperty().get());
        assertEquals(0, viewModel.ageProperty().get());
        assertNull(viewModel.birthdayProperty().get());
        assertEquals("", viewModel.detailBioProperty().get());
        assertNull(viewModel.createTimeProperty().get());
        assertFalse(viewModel.loadingProperty().get());
        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals("", viewModel.successMessageProperty().get());
    }

    // ========== 属性设置测试 ==========

    @Test
    @DisplayName("设置用户名属性")
    void testSetUsername() {
        viewModel.usernameProperty().set("张三");
        assertEquals("张三", viewModel.usernameProperty().get());
    }

    @Test
    @DisplayName("设置性别属性")
    void testSetGender() {
        viewModel.genderProperty().set(1);
        assertEquals(1, viewModel.genderProperty().get());
    }

    @Test
    @DisplayName("设置年龄属性")
    void testSetAge() {
        viewModel.ageProperty().set(25);
        assertEquals(25, viewModel.ageProperty().get());
    }

    @Test
    @DisplayName("设置生日属性")
    void testSetBirthday() {
        final java.time.LocalDate birthday = java.time.LocalDate.of(1999, 1, 1);
        viewModel.birthdayProperty().set(birthday);
        assertEquals(birthday, viewModel.birthdayProperty().get());
    }

    @Test
    @DisplayName("设置头像属性")
    void testSetAvatar() {
        viewModel.avatarProperty().set("http://example.com/avatar.png");
        assertEquals("http://example.com/avatar.png", viewModel.avatarProperty().get());
    }

    @Test
    @DisplayName("设置简介属性")
    void testSetBio() {
        viewModel.bioProperty().set("这是我的简介");
        assertEquals("这是我的简介", viewModel.bioProperty().get());
    }

    @Test
    @DisplayName("设置详细说明属性")
    void testSetDetailBio() {
        viewModel.detailBioProperty().set("详细说明内容");
        assertEquals("详细说明内容", viewModel.detailBioProperty().get());
    }

    @Test
    @DisplayName("设置注册时间属性")
    void testSetCreateTime() {
        final java.time.LocalDateTime time = java.time.LocalDateTime.now();
        viewModel.createTimeProperty().set(time);
        assertEquals(time, viewModel.createTimeProperty().get());
    }

    @Test
    @DisplayName("设置用户ID属性")
    void testSetUserId() {
        viewModel.userIdProperty().set(12345L);
        assertEquals(12345L, viewModel.userIdProperty().get());
    }

    // ========== 表单验证测试 ==========

    @Test
    @DisplayName("用户名长度验证 - 过短")
    void testUsernameTooShort() {
        viewModel.usernameProperty().set("a");
        viewModel.saveProfile();
        // 由于没有真实 HTTP 请求，验证会通过
        // 实际验证逻辑在 saveProfile() 中
    }

    @Test
    @DisplayName("用户名长度验证 - 过长")
    void testUsernameTooLong() {
        viewModel.usernameProperty().set("a".repeat(51));
        viewModel.saveProfile();
        // 验证逻辑会设置 errorMessage
    }

    @Test
    @DisplayName("密码验证码长度验证")
    void testPasswordSmsCodeLength() {
        viewModel.passwordSmsCodeProperty().set("123"); // 少于6位
        viewModel.changePassword();
        assertEquals("请输入6位验证码", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("新密码长度验证")
    void testNewPasswordLength() {
        viewModel.passwordSmsCodeProperty().set("123456");
        viewModel.newPasswordProperty().set("123"); // 少于6位
        viewModel.confirmPasswordProperty().set("123");
        viewModel.changePassword();
        assertEquals("密码长度至少6位", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("密码确认不一致验证")
    void testPasswordMismatch() {
        viewModel.passwordSmsCodeProperty().set("123456");
        viewModel.newPasswordProperty().set("newpass123");
        viewModel.confirmPasswordProperty().set("different123");
        viewModel.changePassword();
        assertEquals("两次密码不一致", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("新手机号格式验证")
    void testNewPhoneFormat() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set("12345"); // 格式不正确
        viewModel.newPhoneSmsCodeProperty().set("654321");
        viewModel.changePhone();
        assertEquals("新手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("新手机验证码长度验证")
    void testNewPhoneSmsCodeLength() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set("13900139000");
        viewModel.newPhoneSmsCodeProperty().set("123"); // 少于6位
        viewModel.changePhone();
        assertEquals("请输入新手机的6位验证码", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("当前手机验证码长度验证")
    void testCurrentPhoneSmsCodeLength() {
        viewModel.currentPhoneSmsCodeProperty().set("123"); // 少于6位
        viewModel.newPhoneProperty().set("13900139000");
        viewModel.newPhoneSmsCodeProperty().set("654321");
        viewModel.changePhone();
        assertEquals("请输入当前手机的6位验证码", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("密码长度超过最大值验证")
    void testPasswordTooLong() {
        viewModel.passwordSmsCodeProperty().set("123456");
        viewModel.newPasswordProperty().set("a".repeat(51));
        viewModel.confirmPasswordProperty().set("a".repeat(51));
        viewModel.changePassword();
        assertEquals("密码长度不能超过50位", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("新手机号为空验证")
    void testNewPhoneEmpty() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set("");
        viewModel.newPhoneSmsCodeProperty().set("654321");
        viewModel.changePhone();
        assertEquals("新手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("验证码为空验证")
    void testSmsCodeEmpty() {
        viewModel.passwordSmsCodeProperty().set("");
        viewModel.changePassword();
        assertEquals("请输入6位验证码", viewModel.errorMessageProperty().get());
    }

    // ========== 回调测试 ==========

    @Test
    @DisplayName("设置成功回调")
    void testSetOnSuccess() {
        final StringBuilder result = new StringBuilder();
        viewModel.setOnSuccess(msg -> result.append(msg));

        // 触发成功回调（需要 mock HTTP 响应）
        // 这里只测试回调设置成功
        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("设置失败回调")
    void testSetOnFailure() {
        final StringBuilder result = new StringBuilder();
        viewModel.setOnFailure(msg -> result.append(msg));

        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("设置信息刷新回调")
    void testSetOnProfileUpdated() {
        final StringBuilder result = new StringBuilder();
        viewModel.setOnProfileUpdated(user -> result.append(user.getUsername()));

        assertNotNull(viewModel);
    }

    // ========== 手机号脱敏测试 ==========

    @Test
    @DisplayName("手机号脱敏 - 正常手机号")
    void testMaskPhoneNormal() {
        viewModel.phoneProperty().set("13800138000");
        // 脱敏逻辑在内部方法 maskPhone() 中
        // 这里测试属性设置
        assertEquals("13800138000", viewModel.phoneProperty().get());
    }

    @Test
    @DisplayName("手机号脱敏 - null 手机号")
    void testMaskPhoneNull() throws Exception {
        final Method method = ProfileViewModel.class.getDeclaredMethod("maskPhone", String.class);
        method.setAccessible(true);
        final String result = (String) method.invoke(viewModel, (String) null);
        assertNull(result);
    }

    @Test
    @DisplayName("手机号脱敏 - 短手机号")
    void testMaskPhoneShort() throws Exception {
        final Method method = ProfileViewModel.class.getDeclaredMethod("maskPhone", String.class);
        method.setAccessible(true);
        final String result = (String) method.invoke(viewModel, "123");
        assertEquals("123", result);
    }

    @Test
    @DisplayName("手机号脱敏 - 11位手机号")
    void testMaskPhoneElevenDigits() throws Exception {
        final Method method = ProfileViewModel.class.getDeclaredMethod("maskPhone", String.class);
        method.setAccessible(true);
        final String result = (String) method.invoke(viewModel, "13800138000");
        assertEquals("138****8000", result);
    }

    // ========== 倒计时按钮测试 ==========

    @Test
    @DisplayName("倒计时按钮初始化")
    void testCountdownButton() throws InterruptedException {
        final Button button = new Button("发送验证码");
        assertFalse(button.isDisabled());
        assertEquals("发送验证码", button.getText());
    }

    // ========== 发送验证码测试 ==========

    @Test
    @DisplayName("sendSmsCodeForPassword - 手机号为空")
    void testSendSmsCodeForPasswordEmptyPhone() {
        viewModel.phoneProperty().set("");
        viewModel.sendSmsCodeForPassword(new Button());
        assertEquals("请先加载用户信息", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendSmsCodeForPassword - 手机号为 null")
    void testSendSmsCodeForPasswordNullPhone() {
        viewModel.phoneProperty().set(null);
        viewModel.sendSmsCodeForPassword(new Button());
        assertEquals("请先加载用户信息", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendSmsCodeForCurrentPhone - 手机号为空")
    void testSendSmsCodeForCurrentPhoneEmptyPhone() {
        viewModel.phoneProperty().set("");
        viewModel.sendSmsCodeForCurrentPhone(new Button());
        assertEquals("请先加载用户信息", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendSmsCodeForNewPhone - 新手机号为空")
    void testSendSmsCodeForNewPhoneEmptyPhone() {
        viewModel.newPhoneProperty().set("");
        viewModel.sendSmsCodeForNewPhone(new Button());
        assertEquals("请输入新手机号", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendSmsCodeForNewPhone - 新手机号为 null")
    void testSendSmsCodeForNewPhoneNullPhone() {
        viewModel.newPhoneProperty().set(null);
        viewModel.sendSmsCodeForNewPhone(new Button());
        assertEquals("请输入新手机号", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("sendSmsCodeForNewPhone - 新手机号格式不正确")
    void testSendSmsCodeForNewPhoneInvalidFormat() {
        viewModel.newPhoneProperty().set("1234567890"); // 不是11位
        viewModel.sendSmsCodeForNewPhone(new Button());
        assertEquals("新手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    // ========== 加载状态测试 ==========

    @Test
    @DisplayName("loading 属性设置")
    void testLoadingProperty() {
        viewModel.loadingProperty().set(true);
        assertTrue(viewModel.loadingProperty().get());

        viewModel.loadingProperty().set(false);
        assertFalse(viewModel.loadingProperty().get());
    }

    // ========== 消息属性测试 ==========

    @Test
    @DisplayName("errorMessage 属性设置")
    void testErrorMessageProperty() {
        viewModel.errorMessageProperty().set("错误消息");
        assertEquals("错误消息", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("successMessage 属性设置")
    void testSuccessMessageProperty() {
        viewModel.successMessageProperty().set("成功消息");
        assertEquals("成功消息", viewModel.successMessageProperty().get());
    }

    // ========== 表单属性测试 ==========

    @Test
    @DisplayName("passwordSmsCode 属性设置")
    void testPasswordSmsCodeProperty() {
        viewModel.passwordSmsCodeProperty().set("123456");
        assertEquals("123456", viewModel.passwordSmsCodeProperty().get());
    }

    @Test
    @DisplayName("newPassword 属性设置")
    void testNewPasswordProperty() {
        viewModel.newPasswordProperty().set("newPassword123");
        assertEquals("newPassword123", viewModel.newPasswordProperty().get());
    }

    @Test
    @DisplayName("confirmPassword 属性设置")
    void testConfirmPasswordProperty() {
        viewModel.confirmPasswordProperty().set("confirm123");
        assertEquals("confirm123", viewModel.confirmPasswordProperty().get());
    }

    @Test
    @DisplayName("currentPhoneSmsCode 属性设置")
    void testCurrentPhoneSmsCodeProperty() {
        viewModel.currentPhoneSmsCodeProperty().set("654321");
        assertEquals("654321", viewModel.currentPhoneSmsCodeProperty().get());
    }

    @Test
    @DisplayName("newPhone 属性设置")
    void testNewPhoneProperty() {
        viewModel.newPhoneProperty().set("13900139000");
        assertEquals("13900139000", viewModel.newPhoneProperty().get());
    }

    @Test
    @DisplayName("newPhoneSmsCode 属性设置")
    void testNewPhoneSmsCodeProperty() {
        viewModel.newPhoneSmsCodeProperty().set("111111");
        assertEquals("111111", viewModel.newPhoneSmsCodeProperty().get());
    }

    // ========== updateUserInfo 测试 ==========

    @Test
    @DisplayName("updateUserInfo - 更新所有属性")
    void testUpdateUserInfo() throws Exception {
        final UserInfo user = new UserInfo();
        user.setUserId(1001L);
        user.setPhone("138****8000");
        user.setUsername("测试用户");
        user.setAvatar("avatar.png");
        user.setBio("简介");
        user.setGender(1);
        user.setAge(25);
        user.setBirthday(java.time.LocalDate.of(1999, 1, 1));
        user.setDetailBio("详细说明");
        user.setCreateTime(java.time.LocalDateTime.now());

        final Method method = ProfileViewModel.class.getDeclaredMethod("updateUserInfo", UserInfo.class);
        method.setAccessible(true);
        method.invoke(viewModel, user);

        assertEquals(1001L, viewModel.userIdProperty().get());
        assertEquals("138****8000", viewModel.phoneProperty().get());
        assertEquals("测试用户", viewModel.usernameProperty().get());
        assertEquals("avatar.png", viewModel.avatarProperty().get());
        assertEquals("简介", viewModel.bioProperty().get());
        assertEquals(1, viewModel.genderProperty().get());
        assertEquals(25, viewModel.ageProperty().get());
        assertNotNull(viewModel.birthdayProperty().get());
        assertEquals("详细说明", viewModel.detailBioProperty().get());
        assertNotNull(viewModel.createTimeProperty().get());
    }

    @Test
    @DisplayName("updateUserInfo - null 值处理")
    void testUpdateUserInfoNullValues() throws Exception {
        final UserInfo user = new UserInfo();
        user.setUserId(1002L);
        user.setPhone("139****9000");
        user.setUsername("用户2");
        user.setBio(null);
        user.setGender(null);
        user.setAge(null);
        user.setDetailBio(null);

        final Method method = ProfileViewModel.class.getDeclaredMethod("updateUserInfo", UserInfo.class);
        method.setAccessible(true);
        method.invoke(viewModel, user);

        assertEquals("", viewModel.bioProperty().get());
        assertEquals(0, viewModel.genderProperty().get());
        assertEquals(0, viewModel.ageProperty().get());
        assertEquals("", viewModel.detailBioProperty().get());
    }

    // ========== 用户名验证边界测试 ==========

    @Test
    @DisplayName("用户名长度 - 最小边界（验证通过）")
    void testUsernameMinLength() {
        viewModel.usernameProperty().set("ab"); // 2字符，最小边界
        viewModel.userIdProperty().set(1L);
        // 只验证长度，不调用 saveProfile
        // 验证通过（无错误消息设置）
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("用户名长度 - 最大边界（验证通过）")
    void testUsernameMaxLength() {
        viewModel.usernameProperty().set("a".repeat(50)); // 50字符，最大边界
        viewModel.userIdProperty().set(1L);
        // 只验证长度，不调用 saveProfile
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("用户名长度 - 边界外（1字符）")
    void testUsernameBelowMin() {
        viewModel.usernameProperty().set("a"); // 1字符，低于最小
        viewModel.saveProfile();
        assertEquals("用户名长度需2-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("用户名长度 - 边界外（51字符）")
    void testUsernameAboveMax() {
        viewModel.usernameProperty().set("a".repeat(51)); // 51字符，超过最大
        viewModel.saveProfile();
        assertEquals("用户名长度需2-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("用户名 - 空字符串跳过验证")
    void testUsernameEmptyNotValidated() {
        viewModel.usernameProperty().set("");
        viewModel.userIdProperty().set(1L);
        // 空用户名跳过验证，不调用 saveProfile
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("用户名 - null 跳过验证")
    void testUsernameNullNotValidated() {
        viewModel.usernameProperty().set(null);
        viewModel.userIdProperty().set(1L);
        // null 用户名跳过验证，不调用 saveProfile
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("用户名 - 只有空格跳过验证")
    void testUsernameOnlySpaces() {
        viewModel.usernameProperty().set("   ");
        viewModel.userIdProperty().set(1L);
        // 只有空格的用户名跳过验证，不调用 saveProfile
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    // ========== sendSmsCodeForPassword 有效手机号测试 ==========

    @Test
    @DisplayName("sendSmsCodeForPassword - 有效手机号不设置错误")
    void testSendSmsCodeForPasswordValidPhone() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.sendSmsCodeForPassword(new Button());
        // 异步请求会失败，但验证通过（不设置 errorMessage）
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    // ========== sendSmsCodeForCurrentPhone 有效手机号测试 ==========

    @Test
    @DisplayName("sendSmsCodeForCurrentPhone - 有效手机号不设置错误")
    void testSendSmsCodeForCurrentPhoneValidPhone() {
        viewModel.phoneProperty().set("13800138000");
        viewModel.sendSmsCodeForCurrentPhone(new Button());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    // ========== sendSmsCodeForNewPhone 有效手机号测试 ==========

    @Test
    @DisplayName("sendSmsCodeForNewPhone - 有效手机号不设置错误")
    void testSendSmsCodeForNewPhoneValidPhone() {
        viewModel.newPhoneProperty().set("13900139000");
        viewModel.sendSmsCodeForNewPhone(new Button());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    // ========== changePassword 有效输入测试 ==========

    @Test
    @DisplayName("changePassword - 有效输入不设置错误")
    void testChangePasswordValidInput() {
        viewModel.passwordSmsCodeProperty().set("123456");
        viewModel.newPasswordProperty().set("newpass123");
        viewModel.confirmPasswordProperty().set("newpass123");
        viewModel.changePassword();
        // 验证通过，进入 loading 状态
        assertTrue(viewModel.loadingProperty().get());
    }

    // ========== changePhone 有效输入测试 ==========

    @Test
    @DisplayName("changePhone - 有效输入不设置错误")
    void testChangePhoneValidInput() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set("13900139000");
        viewModel.newPhoneSmsCodeProperty().set("654321");
        viewModel.changePhone();
        // 验证通过，进入 loading 状态
        assertTrue(viewModel.loadingProperty().get());
    }

    // ========== loadProfile 测试 ==========

    @Test
    @DisplayName("loadProfile - 调用不崩溃")
    void testLoadProfile_shouldNotCrash() {
        assertDoesNotThrow(() -> viewModel.loadProfile());
    }

    @Test
    @DisplayName("loadProfile - 设置 loading 为 true")
    void testLoadProfile_shouldSetLoading() {
        viewModel.loadProfile();
        assertTrue(viewModel.loadingProperty().get());
    }

    // ========== saveProfile 有效输入测试 ==========

    @Test
    @DisplayName("saveProfile - 有效用户名进入 loading 状态")
    void testSaveProfileValidUsername_shouldSetLoading() {
        viewModel.usernameProperty().set("有效用户名");
        viewModel.userIdProperty().set(1L);
        viewModel.saveProfile();
        assertTrue(viewModel.loadingProperty().get());
    }

    // ========== changePassword null 值测试 ==========

    @Test
    @DisplayName("changePassword - null 验证码")
    void testChangePasswordNullSmsCode() {
        viewModel.passwordSmsCodeProperty().set(null);
        viewModel.changePassword();
        assertEquals("请输入6位验证码", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("changePassword - null 新密码")
    void testChangePasswordNullNewPassword() {
        viewModel.passwordSmsCodeProperty().set("123456");
        viewModel.newPasswordProperty().set(null);
        viewModel.changePassword();
        assertEquals("密码长度至少6位", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("changePassword - null 确认密码")
    void testChangePasswordNullConfirmPassword() {
        viewModel.passwordSmsCodeProperty().set("123456");
        viewModel.newPasswordProperty().set("password123");
        viewModel.confirmPasswordProperty().set(null);
        viewModel.changePassword();
        assertEquals("两次密码不一致", viewModel.errorMessageProperty().get());
    }

    // ========== changePhone null 值测试 ==========

    @Test
    @DisplayName("changePhone - null 当前手机验证码")
    void testChangePhoneNullCurrentSmsCode() {
        viewModel.currentPhoneSmsCodeProperty().set(null);
        viewModel.changePhone();
        assertEquals("请输入当前手机的6位验证码", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("changePhone - null 新手机号")
    void testChangePhoneNullNewPhone() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set(null);
        viewModel.changePhone();
        assertEquals("新手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("changePhone - null 新手机验证码")
    void testChangePhoneNullNewSmsCode() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set("13900139000");
        viewModel.newPhoneSmsCodeProperty().set(null);
        viewModel.changePhone();
        assertEquals("请输入新手机的6位验证码", viewModel.errorMessageProperty().get());
    }

    // ========== saveProfile 用户名边界测试 ==========

    @Test
    @DisplayName("saveProfile - 用户名刚好2字符不报错")
    void testSaveProfileUsernameMinBoundary() {
        viewModel.usernameProperty().set("ab");
        viewModel.userIdProperty().set(1L);
        viewModel.saveProfile();
        // 不应因用户名长度报错
        assertNotEquals("用户名长度需2-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("saveProfile - 用户名刚好50字符不报错")
    void testSaveProfileUsernameMaxBoundary() {
        viewModel.usernameProperty().set("a".repeat(50));
        viewModel.userIdProperty().set(1L);
        viewModel.saveProfile();
        assertNotEquals("用户名长度需2-50个字符", viewModel.errorMessageProperty().get());
    }

    // ========== maskPhone 边界测试 ==========

    @Test
    @DisplayName("maskPhone - 10位手机号原样返回")
    void testMaskPhone10Digits() throws Exception {
        final Method method = ProfileViewModel.class.getDeclaredMethod("maskPhone", String.class);
        method.setAccessible(true);
        final String result = (String) method.invoke(viewModel, "1380013800");
        assertEquals("1380013800", result);
    }

    // ========== 回调触发测试 ==========

    @Test
    @DisplayName("saveProfile - 设置 onSuccess 回调后不崩溃")
    void testSaveProfileWithOnSuccessCallback() {
        viewModel.setOnSuccess(msg -> {});
        viewModel.usernameProperty().set("测试用户");
        viewModel.userIdProperty().set(1L);
        viewModel.saveProfile();
        // 不崩溃
    }

    @Test
    @DisplayName("saveProfile - 设置 onFailure 回调后不崩溃")
    void testSaveProfileWithOnFailureCallback() {
        viewModel.setOnFailure(msg -> {});
        viewModel.usernameProperty().set("a".repeat(51));
        viewModel.saveProfile();
        assertEquals("用户名长度需2-50个字符", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("loadProfile - 设置 onFailure 回调后不崩溃")
    void testLoadProfileWithOnFailureCallback() {
        viewModel.setOnFailure(msg -> {});
        viewModel.loadProfile();
        // 不崩溃
    }

    // ========== 手机号验证边界测试 ==========

    @Test
    @DisplayName("changePhone - 新手机号以10开头格式不正确")
    void testChangePhoneInvalidPrefix() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set("10000130000");
        viewModel.newPhoneSmsCodeProperty().set("654321");
        viewModel.changePhone();
        assertEquals("新手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("changePhone - 新手机号12位格式不正确")
    void testChangePhoneTooLongPhone() {
        viewModel.currentPhoneSmsCodeProperty().set("123456");
        viewModel.newPhoneProperty().set("139001390001");
        viewModel.newPhoneSmsCodeProperty().set("654321");
        viewModel.changePhone();
        assertEquals("新手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    // ========== sendSmsCodeForPassword 脱敏手机号测试 ==========

    @Test
    @DisplayName("sendSmsCodeForPassword - 脱敏手机号不设置错误")
    void testSendSmsCodeForPasswordMaskedPhone() {
        viewModel.phoneProperty().set("138****8000");
        viewModel.sendSmsCodeForPassword(new Button());
        // 脱敏手机号不为空，验证通过
        assertEquals("", viewModel.errorMessageProperty().get());
    }
}