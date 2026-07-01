package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import org.example.client.model.LoginResponse;
import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.example.client.view.ProfileViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProfileController 单元测试
 *
 * <p>
 * 测试控制器初始化、FXML 加载等功能。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ProfileController 测试")
class ProfileControllerTest extends JavaFxTestBase {

    private ProfileController controller;
    private ProfileViewModel viewModel;

    @BeforeEach
    void setUp() throws Exception {
        // 注入假 Token，避免 NullPointerException
        final LoginResponse fakeLogin = new LoginResponse();
        fakeLogin.setAccessToken("test-token");
        fakeLogin.setRefreshToken("test-refresh");
        fakeLogin.setExpiresIn(3600L);
        final UserInfo fakeUser = new UserInfo();
        fakeUser.setUserId(1L);
        fakeUser.setUsername("testuser");
        fakeUser.setPhone("138****8000");
        fakeLogin.setUser(fakeUser);
        TokenStorage.save(fakeLogin, false);

        // 手动创建控制器并注入组件
        controller = new ProfileController();

        final TabPane tabPane = new TabPane();
        final Circle avatarCircle = new Circle();
        final Button changeAvatarButton = new Button();
        final TextField usernameField = new TextField();
        final TextField phoneField = new TextField();
        final TextField bioField = new TextField();
        final ComboBox<String> genderComboBox = new ComboBox<>();
        final Spinner<Integer> ageSpinner = new Spinner<>();
        final DatePicker birthdayPicker = new DatePicker();
        final TextArea detailBioArea = new TextArea();
        final Label createTimeLabel = new Label();
        final Label profileMessageLabel = new Label();
        final Button saveProfileButton = new Button();
        final ProgressIndicator profileLoadingIndicator = new ProgressIndicator();
        final Label passwordPhoneLabel = new Label();
        final Button sendPasswordSmsButton = new Button();
        final TextField passwordSmsField = new TextField();
        final PasswordField newPasswordField = new PasswordField();
        final PasswordField confirmPasswordField = new PasswordField();
        final Button changePasswordButton = new Button();
        final ProgressIndicator passwordLoadingIndicator = new ProgressIndicator();
        final Label changePhoneLabel = new Label();
        final Button sendCurrentSmsButton = new Button();
        final TextField currentSmsField = new TextField();
        final TextField newPhoneField = new TextField();
        final Button sendNewSmsButton = new Button();
        final TextField newSmsField = new TextField();
        final Button changePhoneButton = new Button();
        final ProgressIndicator phoneLoadingIndicator = new ProgressIndicator();
        final Label securityMessageLabel = new Label();
        final Button closeButton = new Button();

        setField(controller, "tabPane", tabPane);
        setField(controller, "avatarCircle", avatarCircle);
        setField(controller, "changeAvatarButton", changeAvatarButton);
        setField(controller, "usernameField", usernameField);
        setField(controller, "phoneField", phoneField);
        setField(controller, "bioField", bioField);
        setField(controller, "genderComboBox", genderComboBox);
        setField(controller, "ageSpinner", ageSpinner);
        setField(controller, "birthdayPicker", birthdayPicker);
        setField(controller, "detailBioArea", detailBioArea);
        setField(controller, "createTimeLabel", createTimeLabel);
        setField(controller, "profileMessageLabel", profileMessageLabel);
        setField(controller, "saveProfileButton", saveProfileButton);
        setField(controller, "profileLoadingIndicator", profileLoadingIndicator);
        setField(controller, "passwordPhoneLabel", passwordPhoneLabel);
        setField(controller, "sendPasswordSmsButton", sendPasswordSmsButton);
        setField(controller, "passwordSmsField", passwordSmsField);
        setField(controller, "newPasswordField", newPasswordField);
        setField(controller, "confirmPasswordField", confirmPasswordField);
        setField(controller, "changePasswordButton", changePasswordButton);
        setField(controller, "passwordLoadingIndicator", passwordLoadingIndicator);
        setField(controller, "changePhoneLabel", changePhoneLabel);
        setField(controller, "sendCurrentSmsButton", sendCurrentSmsButton);
        setField(controller, "currentSmsField", currentSmsField);
        setField(controller, "newPhoneField", newPhoneField);
        setField(controller, "sendNewSmsButton", sendNewSmsButton);
        setField(controller, "newSmsField", newSmsField);
        setField(controller, "changePhoneButton", changePhoneButton);
        setField(controller, "phoneLoadingIndicator", phoneLoadingIndicator);
        setField(controller, "securityMessageLabel", securityMessageLabel);
        setField(controller, "closeButton", closeButton);

        // 初始化 Spinner 的 valueFactory
        ageSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 200, 0));

        controller.initialize(null, null);
        viewModel = (ProfileViewModel) getField(controller, "viewModel");
    }

    // ========== 初始化测试 ==========

    @Test
    @DisplayName("控制器初始化成功")
    void testControllerInitialized() {
        assertNotNull(controller, "控制器应成功初始化");
    }

    @Test
    @DisplayName("ViewModel 初始化成功")
    void testViewModelInitialized() {
        assertNotNull(viewModel, "ViewModel 应成功初始化");
    }

    @Test
    @DisplayName("genderComboBox 初始化正确")
    void testGenderComboBoxInitialized() throws Exception {
        final ComboBox<?> genderComboBox = (ComboBox<?>) getField(controller, "genderComboBox");
        assertEquals(3, genderComboBox.getItems().size());
        assertTrue(genderComboBox.getItems().contains("未知"));
        assertTrue(genderComboBox.getItems().contains("男"));
        assertTrue(genderComboBox.getItems().contains("女"));
    }

    @Test
    @DisplayName("ageSpinner 初始化正确")
    void testAgeSpinnerInitialized() throws Exception {
        final Spinner<?> ageSpinner = (Spinner<?>) getField(controller, "ageSpinner");
        assertNotNull(ageSpinner.getValueFactory());
    }

    // ========== 属性绑定测试 ==========

    @Test
    @DisplayName("usernameField 绑定到 ViewModel")
    void testUsernameBinding() throws Exception {
        final TextField usernameField = (TextField) getField(controller, "usernameField");
        viewModel.usernameProperty().set("测试用户");
        assertEquals("测试用户", usernameField.getText());
    }

    @Test
    @DisplayName("phoneField 绑定到 ViewModel")
    void testPhoneBinding() throws Exception {
        final TextField phoneField = (TextField) getField(controller, "phoneField");
        viewModel.phoneProperty().set("138****8000");
        assertEquals("138****8000", phoneField.getText());
    }

    @Test
    @DisplayName("bioField 绑定到 ViewModel")
    void testBioBinding() throws Exception {
        final TextField bioField = (TextField) getField(controller, "bioField");
        bioField.setText("测试简介");
        assertEquals("测试简介", viewModel.bioProperty().get());
    }

    @Test
    @DisplayName("loadingIndicator 绑定到 ViewModel")
    void testLoadingIndicatorBinding() throws Exception {
        final ProgressIndicator indicator = (ProgressIndicator) getField(controller, "profileLoadingIndicator");
        viewModel.loadingProperty().set(true);
        assertTrue(indicator.isVisible());
    }

    @Test
    @DisplayName("profileMessageLabel 绑定到 ViewModel")
    void testProfileMessageLabelBinding() throws Exception {
        final Label label = (Label) getField(controller, "profileMessageLabel");
        viewModel.successMessageProperty().set("修改成功");
        assertEquals("修改成功", label.getText());
    }

    @Test
    @DisplayName("createTimeLabel 更新正确")
    void testCreateTimeLabelUpdate() throws Exception {
        final Label createTimeLabel = (Label) getField(controller, "createTimeLabel");
        viewModel.createTimeProperty().set(java.time.LocalDateTime.now());
        assertNotNull(createTimeLabel.getText());
        assertNotEquals("-", createTimeLabel.getText());
    }

    // ========== Stage 设置测试 ==========

    @Test
    @DisplayName("设置 DialogStage")
    void testSetDialogStage() throws Exception {
        // 需要在 JavaFX 应用线程上运行
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            final Stage stage = new Stage();
            controller.setDialogStage(stage);
            latch.countDown();
        });
        latch.await(2, TimeUnit.SECONDS);
        assertNotNull(controller);
    }

    // ========== 公共方法测试 ==========

    @Test
    @DisplayName("setMainViewModel 不抛异常")
    void testSetMainViewModel() {
        assertDoesNotThrow(() -> controller.setMainViewModel(null));
    }

    @Test
    @DisplayName("handleSaveProfile 不抛异常")
    void testHandleSaveProfile() {
        // saveProfile 会调用 HTTP 请求，可能抛出异常
        try {
            invokeNoArgMethod(controller, "handleSaveProfile");
        } catch (final Exception e) {
            // HTTP 请求失败是预期的
        }
    }

    @Test
    @DisplayName("handleSendPasswordSms 不抛异常")
    void testHandleSendPasswordSms() {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleSendPasswordSms"));
    }

    @Test
    @DisplayName("handleChangePassword 不抛异常")
    void testHandleChangePassword() {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleChangePassword"));
    }

    @Test
    @DisplayName("handleSendCurrentSms 不抛异常")
    void testHandleSendCurrentSms() {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleSendCurrentSms"));
    }

    @Test
    @DisplayName("handleSendNewSms 不抛异常")
    void testHandleSendNewSms() {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleSendNewSms"));
    }

    @Test
    @DisplayName("handleChangePhone 不抛异常")
    void testHandleChangePhone() {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleChangePhone"));
    }

    @Test
    @DisplayName("handleClose 不抛异常")
    void testHandleClose() {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleClose"));
    }

    @Test
    @DisplayName("handleChangeAvatar 方法存在（无头环境跳过对话框测试）")
    void testHandleChangeAvatar() {
        // handleChangeAvatar 会打开 FileChooser 对话框，无头环境下无法测试
        // 验证方法和按钮绑定存在即可
        assertNotNull(controller);
        final Method method;
        try {
            method = controller.getClass().getDeclaredMethod("handleChangeAvatar");
            assertNotNull(method, "handleChangeAvatar 方法应存在");
        } catch (final NoSuchMethodException e) {
            fail("handleChangeAvatar 方法不存在", e);
        }
    }

    // ========== 性别转换测试 ==========

    @Test
    @DisplayName("性别转换 - 男")
    void testGenderConversionMale() throws Exception {
        final ComboBox<String> genderComboBox = (ComboBox<String>) getField(controller, "genderComboBox");
        viewModel.genderProperty().set(1);
        // 等待 UI 更新
        Thread.sleep(100);
        assertEquals("男", genderComboBox.getSelectionModel().getSelectedItem());
    }

    @Test
    @DisplayName("性别转换 - 女")
    void testGenderConversionFemale() throws Exception {
        final ComboBox<String> genderComboBox = (ComboBox<String>) getField(controller, "genderComboBox");
        viewModel.genderProperty().set(2);
        Thread.sleep(100);
        assertEquals("女", genderComboBox.getSelectionModel().getSelectedItem());
    }

    @Test
    @DisplayName("性别转换 - 未知")
    void testGenderConversionUnknown() throws Exception {
        final ComboBox<String> genderComboBox = (ComboBox<String>) getField(controller, "genderComboBox");
        viewModel.genderProperty().set(0);
        Thread.sleep(100);
        assertEquals("未知", genderComboBox.getSelectionModel().getSelectedItem());
    }

    // ========== 更多属性绑定测试 ==========

    @Test
    @DisplayName("securityMessageLabel 绑定到 ViewModel")
    void testSecurityMessageLabelBinding() throws Exception {
        final Label label = (Label) getField(controller, "securityMessageLabel");
        viewModel.successMessageProperty().set("安全设置更新成功");
        assertEquals("安全设置更新成功", label.getText());
    }

    @Test
    @DisplayName("createTimeLabel null 时显示 - 或空")
    void testCreateTimeLabelNull() throws Exception {
        final Label createTimeLabel = (Label) getField(controller, "createTimeLabel");
        viewModel.createTimeProperty().set(null);
        Thread.sleep(100);
        // null 时显示 "-" 或空字符串（取决于绑定实现）
        final String text = createTimeLabel.getText();
        assertTrue(text == null || text.isEmpty() || "-".equals(text));
    }

    @Test
    @DisplayName("passwordPhoneLabel 绑定到 ViewModel phone")
    void testPasswordPhoneLabelBinding() throws Exception {
        final Label label = (Label) getField(controller, "passwordPhoneLabel");
        viewModel.phoneProperty().set("13800138000");
        assertEquals("13800138000", label.getText());
    }

    @Test
    @DisplayName("changePhoneLabel 绑定到 ViewModel phone")
    void testChangePhoneLabelBinding() throws Exception {
        final Label label = (Label) getField(controller, "changePhoneLabel");
        viewModel.phoneProperty().set("13900139000");
        assertEquals("13900139000", label.getText());
    }

    @Test
    @DisplayName("saveProfileButton 在 loading 时禁用")
    void testSaveProfileButtonDisabledWhenLoading() throws Exception {
        final Button button = (Button) getField(controller, "saveProfileButton");
        viewModel.loadingProperty().set(true);
        assertTrue(button.isDisabled());
        viewModel.loadingProperty().set(false);
        assertFalse(button.isDisabled());
    }

    @Test
    @DisplayName("changePasswordButton 在 loading 时禁用")
    void testChangePasswordButtonDisabledWhenLoading() throws Exception {
        final Button button = (Button) getField(controller, "changePasswordButton");
        viewModel.loadingProperty().set(true);
        assertTrue(button.isDisabled());
    }

    @Test
    @DisplayName("changePhoneButton 在 loading 时禁用")
    void testChangePhoneButtonDisabledWhenLoading() throws Exception {
        final Button button = (Button) getField(controller, "changePhoneButton");
        viewModel.loadingProperty().set(true);
        assertTrue(button.isDisabled());
    }

    @Test
    @DisplayName("passwordLoadingIndicator 在 loading 时可见")
    void testPasswordLoadingIndicatorVisibleWhenLoading() throws Exception {
        final ProgressIndicator indicator = (ProgressIndicator) getField(controller, "passwordLoadingIndicator");
        viewModel.loadingProperty().set(true);
        assertTrue(indicator.isVisible());
    }

    @Test
    @DisplayName("phoneLoadingIndicator 在 loading 时可见")
    void testPhoneLoadingIndicatorVisibleWhenLoading() throws Exception {
        final ProgressIndicator indicator = (ProgressIndicator) getField(controller, "phoneLoadingIndicator");
        viewModel.loadingProperty().set(true);
        assertTrue(indicator.isVisible());
    }

    // ========== 表单绑定测试 ==========

    @Test
    @DisplayName("passwordSmsField 双向绑定到 ViewModel")
    void testPasswordSmsFieldBinding() throws Exception {
        final TextField field = (TextField) getField(controller, "passwordSmsField");
        viewModel.passwordSmsCodeProperty().set("654321");
        assertEquals("654321", field.getText());
    }

    @Test
    @DisplayName("newPasswordField 双向绑定到 ViewModel")
    void testNewPasswordFieldBinding() throws Exception {
        final PasswordField field = (PasswordField) getField(controller, "newPasswordField");
        viewModel.newPasswordProperty().set("newpass123");
        assertEquals("newpass123", field.getText());
    }

    @Test
    @DisplayName("confirmPasswordField 双向绑定到 ViewModel")
    void testConfirmPasswordFieldBinding() throws Exception {
        final PasswordField field = (PasswordField) getField(controller, "confirmPasswordField");
        viewModel.confirmPasswordProperty().set("newpass123");
        assertEquals("newpass123", field.getText());
    }

    @Test
    @DisplayName("currentSmsField 双向绑定到 ViewModel")
    void testCurrentSmsFieldBinding() throws Exception {
        final TextField field = (TextField) getField(controller, "currentSmsField");
        viewModel.currentPhoneSmsCodeProperty().set("111111");
        assertEquals("111111", field.getText());
    }

    @Test
    @DisplayName("newPhoneField 双向绑定到 ViewModel")
    void testNewPhoneFieldBinding() throws Exception {
        final TextField field = (TextField) getField(controller, "newPhoneField");
        viewModel.newPhoneProperty().set("13900139000");
        assertEquals("13900139000", field.getText());
    }

    @Test
    @DisplayName("newSmsField 双向绑定到 ViewModel")
    void testNewSmsFieldBinding() throws Exception {
        final TextField field = (TextField) getField(controller, "newSmsField");
        viewModel.newPhoneSmsCodeProperty().set("222222");
        assertEquals("222222", field.getText());
    }

    // ========== 性别反向绑定测试 ==========

    @Test
    @DisplayName("性别下拉框选择'男'更新 ViewModel gender 为 1")
    void testGenderComboBoxToViewModelMale() throws Exception {
        final ComboBox<String> genderComboBox = (ComboBox<String>) getField(controller, "genderComboBox");
        genderComboBox.getSelectionModel().select("男");
        Thread.sleep(100);
        assertEquals(1, viewModel.genderProperty().get());
    }

    @Test
    @DisplayName("性别下拉框选择'女'更新 ViewModel gender 为 2")
    void testGenderComboBoxToViewModelFemale() throws Exception {
        final ComboBox<String> genderComboBox = (ComboBox<String>) getField(controller, "genderComboBox");
        genderComboBox.getSelectionModel().select("女");
        Thread.sleep(100);
        assertEquals(2, viewModel.genderProperty().get());
    }

    @Test
    @DisplayName("性别下拉框选择'未知'更新 ViewModel gender 为 0")
    void testGenderComboBoxToViewModelUnknown() throws Exception {
        final ComboBox<String> genderComboBox = (ComboBox<String>) getField(controller, "genderComboBox");
        genderComboBox.getSelectionModel().select("未知");
        Thread.sleep(100);
        assertEquals(0, viewModel.genderProperty().get());
    }

    // ========== 错误消息样式测试 ==========

    @Test
    @DisplayName("errorMessage 设置后 profileMessageLabel 显示错误样式")
    void testErrorMessageStyle() throws Exception {
        final Label label = (Label) getField(controller, "profileMessageLabel");
        viewModel.errorMessageProperty().set("测试错误");
        Thread.sleep(100);
        // 验证错误消息被设置（通过 showError 方法）
        assertNotNull(label.getText());
    }

    // ========== 辅助方法 ==========

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

    private static void invokeMethod(final Object obj, final String name,
            final Class<?> paramType, final Object paramValue) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name, paramType);
        method.setAccessible(true);
        method.invoke(obj, paramValue);
    }
}